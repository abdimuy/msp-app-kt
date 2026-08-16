package com.example.msp_app.core.sync.cobranza

import android.util.Log
import com.example.msp_app.core.telemetry.Telemetry
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

/**
 * Posición de paginación de un recurso de cobranza: las DOS mitades del
 * cursor `(UPDATED_AT, PK)` con las que [CobranzaSyncManager.syncResource]
 * pide la página siguiente.
 *
 * Existe como tipo propio (y no como dos parámetros sueltos) por una sola
 * razón: "¿avanzó el cursor?" es una comparación de igualdad del PAR. El
 * defecto D1 (2026-08-14, zona en campo re-descargando 2,057 pagos cada
 * ~76 s indefinidamente) fue exactamente eso — la mitad `UPDATED_AT` sí se
 * movía dentro de la corrida, pero la posición efectiva al terminar era
 * siempre la misma porque `afterId` no se persistía y volvía a 0.
 */
data class SyncCursorPosition(val cursor: String?, val afterId: Int) {

    /**
     * Token de igualdad estable de esta posición: 8 hex del SHA-256 de
     * `"<cursor>|<afterId>"`.
     *
     * Por qué un token y no los valores crudos: el `afterId` es el PK de un
     * documento de Microsip (`IMPTE_DOCTO_CC_ID` / `DOCTO_CC_ID`) y el cursor
     * es la marca de actualización de ese documento. Nada de eso identifica a
     * una persona, pero tampoco hace falta que salga del teléfono: el
     * diagnóstico sólo necesita saber si dos corridas terminaron en la MISMA
     * posición, y para eso un token de igualdad alcanza.
     *
     * HONESTIDAD SOBRE LO QUE ESTO NO ES: no es anonimización criptográfica.
     * La entrada tiene poca entropía (un timestamp y un entero), así que un
     * hash truncado es forzable por fuerza bruta si alguien se lo propone. Se
     * elige igual porque el payload no carga ningún identificador de documento
     * y porque el valor que aporta —distinguir "atorado en A" de "oscilando
     * entre A y B"— no se consigue con un booleano.
     */
    fun fingerprint(): String {
        if (cursor == null) return NO_POSITION
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$cursor|$afterId".toByteArray(Charsets.UTF_8))
        return digest.take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Posición "sin cursor todavía": arranque limpio o replay por generación. */
        const val NO_POSITION = "start"
        private const val FINGERPRINT_BYTES = 4
    }
}

/** Resultado de una corrida completa de `syncNow()`, tal como se reporta. */
enum class SyncRunOutcome(val wireValue: String) {
    OK("ok"),
    ERROR("error"),
    SKIPPED_OFFLINE("skipped_offline"),
    SKIPPED_NO_ZONE("skipped_no_zone")
}

/**
 * Instrumentación del sync de cobranza sobre el puerto [Telemetry] de
 * `:core:telemetry`.
 *
 * ## Qué problema resuelve
 *
 * El defecto que más costó (D1) sólo era visible midiendo en un teléfono
 * real: el cursor no avanzaba y la zona re-descargaba el mismo lote cada
 * ~76 s, para siempre. Ninguna prueba de escritorio lo mostró y la compuerta
 * estaba en verde. Esta clase existe para que esa CLASE de defecto —no ese
 * defecto puntual, que ya está arreglado— quede escrita en los datos que
 * salen del teléfono, sin necesidad de tener el teléfono en la mano.
 *
 * ## La decisión de diseño que hace que esto sirva de algo
 *
 * "¿Avanzó el cursor?" se responde comparando dónde TERMINÓ esta corrida
 * contra dónde terminó la ANTERIOR ([lastEndPosition], memoria de esta clase).
 * Deliberadamente NO se compara contra el estado persistido en
 * `cobranza_sync_state`, y la razón es la única que importa: el defecto D1
 * consistía precisamente en que ese estado no se persistía. Una telemetría que
 * lea la misma fuente rota hereda su ceguera —
 *
 *   - código pre-fix: cada corrida arrancaba en `afterId = 0` y terminaba en
 *     `(X, 2057)`. Comparando "arranque contra cierre" DENTRO de la corrida,
 *     la posición sí cambia y el defecto se reporta como sano: falso negativo.
 *   - comparando cierre contra cierre: `(X, 2057)` una y otra vez, corrida
 *     tras corrida. Eso es el bucle, y se ve.
 *
 * Por eso además se emite `resumed`, que es la CAUSA RAÍZ y no el síntoma:
 * ¿esta corrida arrancó donde terminó la anterior? En el código pre-fix la
 * respuesta habría sido `false` en cada corrida — la posición no sobrevivía
 * entre corridas. `advanced=false` dice "estoy atorado"; `resumed=false` dice
 * "estoy atorado PORQUE no recuerdo dónde iba".
 *
 * ## Los tres eventos y por qué son ésos
 *
 * - [EVENT_RESOURCE] `cobranza_sync.resource` — uno por recurso por corrida.
 *   Lleva `pages` y `rows` (el síntoma se reportó como "2,057 fijos cada
 *   76 s": sin conteo de filas no hay "2,057", sin conteo de páginas no se
 *   distingue "una página gorda" de "veinte chicas") y, sobre todo,
 *   `advanced` / `resumed` / `pos_before` / `pos`, explicados arriba.
 * - [EVENT_CURSOR_STALLED] `cobranza_sync.cursor_stalled` — se emite cuando el
 *   recurso lleva [STALL_THRESHOLD] corridas consecutivas cerrando en la misma
 *   posición mientras SIGUE aplicando filas. Ésa es la firma exacta de D1 y el
 *   evento que un tablero puede alertar sin interpretar nada.
 * - [EVENT_RUN] `cobranza_sync.run` — uno por `syncNow()`, incluidos los que
 *   se saltan (offline / sin zona). Lleva `duration_ms`, que es la otra mitad
 *   del síntoma ("cada 76 s"), y `outcome`. Sin este evento un teléfono que
 *   dejó de sincronizar por completo se ve idéntico a uno sano: en ambos casos
 *   simplemente no llegan eventos de recurso.
 *
 * NO hay evento por página, a propósito: engordar cada página con una
 * escritura es justo lo que no debe hacer la telemetría de un lazo que pagina.
 * El techo es de 5 eventos por `syncNow()` (2 recursos × hasta 2 eventos + 1
 * de corrida), independiente de cuántas páginas se bajen.
 *
 * ## Cero datos personales
 *
 * Todo lo que se emite es: la zona (un id de ruta, no de persona), el nombre
 * del recurso (`pagos`/`ventas`, literal de código), conteos, una duración, un
 * enum de resultado y el token de igualdad de [SyncCursorPosition.fingerprint].
 * NO viaja: nombre de cliente, importe, folio, teléfono, dirección,
 * coordenada, id de cliente ni id de cobrador. El puerto [Telemetry] ya
 * prohíbe PII por contrato; acá la garantía es más fuerte porque las CLAVES y
 * los VALORES son constantes de código o números derivados de conteos — no
 * hay ninguna ruta por la que un campo de negocio llegue a `props`.
 *
 * ## No degrada el sync
 *
 * 1. Todas las emisiones pasan por [emit], que traga cualquier `Throwable` y
 *    lo loguea. Si la telemetría falla, el sync sigue.
 *    `CancellationException` SÍ se repropaga (cancelación estructurada — mismo
 *    criterio que `DurableTelemetryQueue`).
 * 2. Sólo se usa [Telemetry.event], NUNCA [Telemetry.error]: el adapter real
 *    (`DurableTelemetry`) implementa `error()` con `runBlocking` para no perder
 *    el evento, y bloquear el hilo del sync viola el requisito de no
 *    degradarlo. El costo de la decisión es que estos eventos viajan en el tier
 *    "ruido" de la cola durable (descartables tras 8 fallos de entrega); se
 *    acepta porque el estancamiento es una condición SOSTENIDA —se re-emite en
 *    cada corrida mientras dure—, así que perder una instancia no pierde la
 *    señal.
 * 3. El conteo de corridas estancadas vive en memoria ([stallRuns]), no en
 *    Room: cero escrituras nuevas a la base del cobrador. Se reinicia al
 *    reiniciar el proceso, lo que es aceptable porque D1 se manifestaba dentro
 *    de una misma sesión (ciclo de ~76 s, indefinido).
 */
class CobranzaSyncTelemetry(private val telemetry: Telemetry) {

    /**
     * Corridas consecutivas que cerraron en la misma posición, por recurso.
     * `ConcurrentHashMap` porque el manager puede emitir desde el tick loop,
     * el observer de conectividad y el de contexto — el mutex de escritura los
     * serializa hoy, pero este mapa no debe depender de eso.
     */
    private val stallRuns = ConcurrentHashMap<String, Int>()

    /**
     * Posición en la que CERRÓ la corrida anterior de cada recurso. Es la
     * memoria que hace detectable a D1 sin depender del estado persistido
     * (ver el KDoc de la clase). Se pierde al reiniciar el proceso, y eso está
     * bien: la primera corrida de un proceso nunca puede estar "estancada"
     * respecto de nada.
     */
    private val lastEndPosition = ConcurrentHashMap<String, SyncCursorPosition>()

    /**
     * Reporta el resultado de paginar un recurso completo.
     *
     * @param before posición con la que ARRANCÓ la corrida, leída del estado
     *   persistido. Sólo se usa para calcular `resumed` (la causa raíz), NUNCA
     *   para decidir si hubo avance.
     * @param after posición en la que la corrida CERRÓ.
     * @return el valor de `advanced` que se reportó, o `null` si la emisión
     *   falló. El manager lo usa únicamente para armar el `progress` del evento
     *   de corrida — ninguna decisión del sync depende de este retorno.
     */
    @Suppress("LongParameterList")
    fun resourceSynced(
        zona: Int,
        resource: String,
        pages: Int,
        rows: Int,
        before: SyncCursorPosition,
        after: SyncCursorPosition,
        epochReplayed: Boolean
    ): Boolean? = emitValue {
        val previousEnd = lastEndPosition.put(resource, after)
        // Primera corrida del proceso: no hay contra qué comparar, así que
        // cuenta como avance (no se puede estar atorado respecto de nada).
        val advanced = previousEnd == null || previousEnd != after
        // ¿Arrancó donde cerró la anterior? Si no, la posición no sobrevivió
        // entre corridas — la causa raíz de D1, no su síntoma.
        val resumed = previousEnd == null || previousEnd == before
        val streak = if (advanced) {
            stallRuns.remove(resource)
            0
        } else {
            stallRuns.merge(resource, 1, Int::plus) ?: 1
        }
        val posAfter = after.fingerprint()
        telemetry.event(
            EVENT_RESOURCE,
            mapOf(
                "zona" to zona.toString(),
                "resource" to resource,
                "pages" to pages.toString(),
                "rows" to rows.toString(),
                "advanced" to advanced.toString(),
                "resumed" to resumed.toString(),
                "pos_before" to (previousEnd?.fingerprint() ?: SyncCursorPosition.NO_POSITION),
                "pos" to posAfter,
                "stall_runs" to streak.toString(),
                "epoch_replay" to epochReplayed.toString()
            )
        )
        // `rows > 0` es parte de la firma y no un detalle: un recurso ya al día
        // también cierra cada corrida en la misma posición, y eso es SALUD, no
        // un bucle. Lo que delata a D1 es seguir aplicando filas sin moverse.
        if (!advanced && rows > 0 && streak >= STALL_THRESHOLD) {
            telemetry.event(
                EVENT_CURSOR_STALLED,
                mapOf(
                    "zona" to zona.toString(),
                    "resource" to resource,
                    "stall_runs" to streak.toString(),
                    "rows" to rows.toString(),
                    "pages" to pages.toString(),
                    "resumed" to resumed.toString(),
                    "pos" to posAfter
                )
            )
        }
        advanced
    }

    /**
     * Reporta el cierre de un `syncNow()`. [zona] es `null` cuando no había
     * contexto (no hay zona que reportar); [advanced] es `null` cuando la
     * corrida ni siquiera llegó a paginar.
     */
    fun runFinished(
        zona: Int?,
        outcome: SyncRunOutcome,
        pages: Int,
        rows: Int,
        durationMillis: Long,
        advanced: Boolean?
    ) = emit {
        telemetry.event(
            EVENT_RUN,
            mapOf(
                "zona" to (zona?.toString() ?: UNKNOWN_ZONE),
                "outcome" to outcome.wireValue,
                "pages" to pages.toString(),
                "rows" to rows.toString(),
                "duration_ms" to durationMillis.toString(),
                "progress" to when (advanced) {
                    null -> PROGRESS_NOT_APPLICABLE
                    true -> PROGRESS_ADVANCED
                    false -> PROGRESS_STALLED
                }
            )
        )
    }

    /**
     * Blindaje: la telemetría NUNCA puede tumbar ni degradar el sync. Traga
     * todo salvo la cancelación cooperativa, que se repropaga.
     */
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> emitValue(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(TAG, "telemetria de sync ignorada (best-effort): ${e.message}", e)
        null
    }

    private inline fun emit(block: () -> Unit) {
        emitValue(block)
    }

    companion object {
        const val EVENT_RESOURCE = "cobranza_sync.resource"
        const val EVENT_CURSOR_STALLED = "cobranza_sync.cursor_stalled"
        const val EVENT_RUN = "cobranza_sync.run"

        /**
         * Corridas consecutivas sin avance (aplicando filas) antes de gritar.
         * Tres, no una: una corrida sin avance es normal (un recurso al día que
         * re-aplica su última página), tres seguidas con filas ya no lo son. Con
         * el tick de 30 s de `CobranzaSyncManager`, tres corridas son ~90 s —
         * mismo orden de magnitud que el ciclo de ~76 s que se midió en campo,
         * así que el aviso llega en el primer par de minutos, no en horas.
         */
        const val STALL_THRESHOLD = 3

        const val PROGRESS_ADVANCED = "advanced"
        const val PROGRESS_STALLED = "stalled"
        const val PROGRESS_NOT_APPLICABLE = "n_a"
        const val UNKNOWN_ZONE = "unknown"

        private const val TAG = "CobranzaSyncTelemetry"
    }
}

/**
 * Implementación nula del puerto [Telemetry] — el default de
 * [CobranzaSyncManager] para que construirlo sin telemetría (pruebas viejas,
 * cualquier call site que no la quiera) siga siendo posible y no emita nada.
 */
object NoOpTelemetry : Telemetry {
    override fun screenView(screen: String) = Unit
    override fun tap(screen: String, element: String) = Unit
    override fun event(name: String, props: Map<String, String>) = Unit
    override fun error(code: String, message: String, props: Map<String, String>) = Unit
}
