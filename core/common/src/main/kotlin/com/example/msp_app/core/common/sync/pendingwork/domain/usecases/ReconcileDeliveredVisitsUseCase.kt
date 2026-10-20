package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.DeliveredVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.DineroPendiente
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.FotoSinEnviar
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.MemoriaDeBarridoPospuesto
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.visitas.envio.ReglaDePoda
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

/**
 * Resultado de una corrida de [ReconcileDeliveredVisitsUseCase].
 *
 * @param revisadas ids distintos que se leyeron como soltados.
 * @param faltantes las que el servidor no tenía y volvieron de verdad a
 *   pendiente (alerta del spec §12-1 si > 0).
 * @param confirmadas las que el servidor nombró y quedaron con
 *   `CONFIRMADA_EN` (las que ya la tenían cuentan y la conservan, E-VIS-028).
 * @param tandasFallidas consultas `by-ids` que no contestaron: sus ids no
 *   cambiaron.
 * @param entregadasIlegibles la lectura local falló: no se preguntó nada.
 * @param idsInvalidos ids leídos que no son UUID canónicos: no se preguntaron
 *   ni cambiaron (ver [ReconcileDeliveredVisitsUseCase]).
 * @param idsAjenos ids que el servidor contestó sin que se le preguntaran
 *   (descartados; `visitas_reconcilia_respuesta_ajena`).
 * @param retenidasPorComprobante nombradas por el servidor que no se
 *   confirmaron porque tienen una foto sin subir
 *   (`visitas_reconcilia_retenidas_por_comprobante`).
 * @param confirmacionesFallidas escrituras de confirmación que fallaron.
 * @param devolucionesFallidas escrituras de devolución a pendiente que fallaron.
 *
 * Los cuatro últimos existen para la telemetría de visitas (Tarea 14, H14):
 * `SyncErrorReporter` termina en `StubTelemetrySink` y no sale del teléfono,
 * así que cada código que reporta esta corrida también se CUENTA aquí y
 * `TelemetriaDeVisitas.conciliacion` lo manda con su lista blanca. Solo
 * conteos: ningún id viaja.
 */
data class ConciliacionInversa(
    val revisadas: Int,
    val faltantes: Int,
    val confirmadas: Int,
    val tandasFallidas: Int,
    val entregadasIlegibles: Boolean = false,
    val idsInvalidos: Int = 0,
    val idsAjenos: Int = 0,
    val retenidasPorComprobante: Int = 0,
    val confirmacionesFallidas: Int = 0,
    val devolucionesFallidas: Int = 0,
    /** Barrido (E-APP-079, E-VIS-031): entregadas devueltas a pendiente por una foto sin enviar. */
    val devueltasPorFoto: Int = 0,
    /**
     * Barrido (E-VIS-032): cuántas fotos sin enviar vio la corrida. TODAS
     * devuelven su visita; ninguna se abandona.
     */
    val fotosSinEnviar: Int = 0,
    /**
     * Barrido (E-VIS-032): de [fotosSinEnviar], las que según el reloj del
     * teléfono llevan más de [ReglaDePoda.FOTOS_SIN_ENVIAR_DIAS] días, o cuya
     * `CREADA_EN` no se puede leer. Reemplaza al antiguo conteo de
     * "abandonadas": antes esas fotos dejaban de viajar; ahora viajan igual y
     * esto es solo la alerta (> 0) de que una foto lleva más de una semana sin
     * llegar.
     */
    val fotosSinEnviarViejas: Int = 0,
    /**
     * Barrido (E-VIS-032): minutos desde la `CREADA_EN` legible más vieja,
     * según el reloj del teléfono, nunca negativo. Nulo sin fotos legibles.
     * Solo telemetría: no decide nada.
     */
    val fotoSinEnviarMasViejaMin: Long? = null,
    /**
     * Barrido: no reabrió nada porque había pagos o ventas por subir (o no se
     * pudo saber). Lo que esperaba sigue en [visitasConFotoEnEspera].
     */
    val barridoPospuestoPorDinero: Boolean = false,
    /**
     * Barrido: visitas con una foto sin enviar que esta pasada NO reabrió (por
     * el tope [ReconcileDeliveredVisitsUseCase.MAX_DEVUELTAS_POR_PASADA] o por
     * el dinero). Pasan a la siguiente; ninguna se abandona.
     */
    val visitasConFotoEnEspera: Int = 0,
    /**
     * Válvula: corridas seguidas con el barrido pospuesto por dinero, contando
     * esta. Vuelve a 0 cuando el barrido corre (normal o por la válvula) o no
     * hay fotos que esperen.
     */
    val barridoPospuestoSeguidas: Int = 0,
    /**
     * Válvula: el barrido corrió aunque había dinero por subir, porque ya
     * llevaba [ReconcileDeliveredVisitsUseCase.POSPUESTAS_ANTES_DE_LA_VALVULA]
     * corridas pospuestas, con tope
     * [ReconcileDeliveredVisitsUseCase.MAX_DEVUELTAS_POR_VALVULA].
     */
    val barridoPorValvula: Boolean = false
)

/**
 * Reconciliación inversa diaria (capa 5; plan visitas P1, Tarea 13; spec
 * §4.6-1; E-VIS-009).
 *
 * ## Qué cierra
 *
 * Antes de Room v35 el teléfono ponía `GUARDADO_EN_MICROSIP = 1` también tras
 * respuestas que no traían la visita (E-VIS-001). Esas legadas, y cualquier
 * visita que el servidor pierda después de confirmarla, se quedaban "enviadas"
 * para siempre. Esta corrida le pregunta al servidor por todo lo soltado y
 * hace lo único que la respuesta permite: si la nombró, la confirma (y desde
 * ahí corre la retención de `ReglaDePoda`); si contestó la tanda y no la
 * nombró, la devuelve a pendiente y lo reporta —el mismo día, no meses
 * después por un cobrador que se queja—.
 *
 * ## Las reglas de seguridad (Review Focus 2 y 3 del plan)
 *
 * - **Una tanda que falla no decide nada**: un `throw` de `by-ids` es
 *   "desconocido", nunca "el servidor no la tiene" (mismo criterio que
 *   [ReconcileVisitsUseCase]). Las demás tandas siguen.
 * - **Un id que no se preguntó no se confirma.** La guarda real es
 *   `si = tanda.filter { it in existentes }`: lo que se confirma sale de la
 *   tanda preguntada, nunca de la respuesta. El filtro de `existentes` y el
 *   reporte de ajenos son diagnóstico encima de eso.
 * - **Reconfirmar no rejuvenece** (E-VIS-028): las confirmadas de la ventana
 *   se preguntan cada día y, si el servidor las nombra, se vuelven a pasar a
 *   [DeliveredVisitsStore.confirmar], que conserva la `CONFIRMADA_EN` que ya
 *   tenían. Por eso `confirmadas` cuenta las nombradas que quedan con marca,
 *   no solo las nuevas.
 * - **La comparación de ids es exacta**, igual que `existenciaCompleta`
 *   (Tarea 9). El servidor contesta `uuid.String()` (minúsculas canónicas) y
 *   el teléfono acuña `UUID.toString()` (también minúsculas): coinciden. Si
 *   algún día no, el error cae del lado seguro: la visita vuelve a pendiente y
 *   el servidor la deduplica al reenviarla; nunca se confirma sin prueba.
 * - Devolver a pendiente nunca pierde datos: lo peor que cuesta es un reenvío.
 * - **Un id que no es UUID canónico no se pregunta ni se decide.** `by-ids`
 *   contesta `422 ids_invalid` a TODA la petición si un solo id no pasa
 *   `uuid.Parse` (msp-api `handlers_visitas_existentes_por_ids.go:143-145`;
 *   `TestHTTP_VisitasExistentesPorIDs_MalformedUUIDAmongValid_Returns422_AndNothingLeaks`).
 *   Mandarlo dejaría sin decidir, todos los días, a las otras 99 de su
 *   tanda. Se separa antes de armar las tandas, se queda como está (la poda
 *   tampoco la suelta sin `CONFIRMADA_EN`) y se reporta con
 *   [ERROR_CODE_ID_INVALIDO]. El filtro es la forma canónica
 *   `8-4-4-4-12` hexadecimal, sin distinguir mayúsculas: un subconjunto de
 *   lo que `uuid.Parse` acepta, así que nada de lo que se manda puede
 *   provocar ese 422. El teléfono solo acuña esa forma (`UUID.toString()`).
 *
 * No encola nada: el worker diario reencola las pendientes con `KEEP` tras
 * correrla. No hay tope de reintentos: lo que no se decidió hoy se pregunta en
 * la siguiente corrida.
 */
@Suppress(
    // Cada decision del barrido (tanda, confirmar, devolver, valvula, dinero
    // pendiente) es su propia funcion con su propio reporte; juntarlas para
    // detekt mezclaria caminos que deciden que visita se reabre.
    "TooManyFunctions"
)
class ReconcileDeliveredVisitsUseCase(
    private val store: DeliveredVisitsStore,
    private val registry: VisitCustodyRegistry,
    private val errorReporter: SyncErrorReporter,
    /** Prioridad del dinero: con pagos o ventas por subir, el barrido no reabre nada. */
    private val dineroPendiente: DineroPendiente = DineroPendiente.NINGUNO,
    /** Válvula: corridas seguidas pospuestas por dinero, entre corridas. */
    private val memoriaDePospuesto: MemoriaDeBarridoPospuesto = MemoriaDeBarridoPospuesto.NINGUNA,
    private val clock: () -> Instant
) {

    suspend fun execute(dias: Int = VENTANA_DIAS): ConciliacionInversa {
        val ahora = clock()
        val desde = AppTime.toWireFormat(ahora.minus(Duration.ofDays(dias.toLong())))
        val ahoraEnCable = AppTime.toWireFormat(ahora)

        val cuenta = Contadores()
        // Primero el barrido: lo que devuelve ya no se lee como soltado.
        barrerFotosSinEnviar(ahora, cuenta)

        val ids = attempt(ERROR_CODE_ENTREGADAS_ILEGIBLES, CONTEXT_LEER) {
            store.entregadasDesde(desde)
        }?.distinct() ?: return ConciliacionInversa(
            0,
            0,
            0,
            0,
            entregadasIlegibles = true,
            devolucionesFallidas = cuenta.devolverFallido,
            devueltasPorFoto = cuenta.devueltasPorFoto,
            fotosSinEnviar = cuenta.fotosSinEnviar,
            fotosSinEnviarViejas = cuenta.fotosSinEnviarViejas,
            fotoSinEnviarMasViejaMin = cuenta.fotoSinEnviarMasViejaMin,
            barridoPospuestoPorDinero = cuenta.pospuesto,
            visitasConFotoEnEspera = cuenta.enEspera,
            barridoPospuestoSeguidas = cuenta.seguidas,
            barridoPorValvula = cuenta.porValvula
        )

        val (validos, invalidos) = separarInvalidos(ids)

        validos.chunked(VisitCustodyRegistry.MAX_IDS_PER_REQUEST).forEach { tanda ->
            decidirTanda(tanda, ahoraEnCable, cuenta)
        }
        val faltantes = cuenta.faltantes

        if (faltantes > 0) {
            errorReporter.report(
                code = ERROR_CODE_DEVUELTA_A_PENDIENTE,
                message = "$CONTEXT_DEVOLVER: el servidor no tenia visitas ya soltadas",
                props = mapOf(
                    PROP_DEVUELTAS to faltantes.toString(),
                    PROP_REVISADAS to ids.size.toString()
                )
            )
        }
        return ConciliacionInversa(
            revisadas = ids.size,
            faltantes = faltantes,
            confirmadas = cuenta.confirmadas,
            tandasFallidas = cuenta.fallidas,
            idsInvalidos = invalidos.size,
            idsAjenos = cuenta.ajenos,
            retenidasPorComprobante = cuenta.retenidas,
            confirmacionesFallidas = cuenta.confirmarFallido,
            devolucionesFallidas = cuenta.devolverFallido,
            devueltasPorFoto = cuenta.devueltasPorFoto,
            fotosSinEnviar = cuenta.fotosSinEnviar,
            fotosSinEnviarViejas = cuenta.fotosSinEnviarViejas,
            fotoSinEnviarMasViejaMin = cuenta.fotoSinEnviarMasViejaMin,
            barridoPospuestoPorDinero = cuenta.pospuesto,
            visitasConFotoEnEspera = cuenta.enEspera,
            barridoPospuestoSeguidas = cuenta.seguidas,
            barridoPorValvula = cuenta.porValvula
        )
    }

    /**
     * Barrido del HALLAZGO de E-APP-076 (E-APP-079). Una visita ENTREGADA
     * (confirmada, o legada sin marcas) con una foto sin `SUBIDA_EN` cuyo
     * archivo existe no la manda nadie: el worker solo toma pendientes y
     * `confirmar` solo la retiene. Se devuelve a pendiente; el reenvío es
     * idempotente por UUID y el servidor guarda la foto que le falta
     * (`planImagenes`, E-VIS-004). No pregunta al servidor: es un hecho local.
     *
     * - **Incluye las legadas (E-VIS-031).** Una legada con foto la nombraba el
     *   servidor, `confirmar` la retenía por la foto y el barrido no la veía
     *   (solo miraba confirmadas): retenida para siempre, la foto sin viajar.
     * - **Nunca abandona (E-VIS-032).** Antes una foto con más de 7 días
     *   según `CREADA_EN` dejaba de devolver su visita. Esa edad sale del reloj
     *   de pared del teléfono: con la fecha atrasada y luego corregida, la foto
     *   se abandonaba sin que pasara un día real. Ahora toda foto que podría
     *   viajar devuelve su visita, en cada corrida. La edad solo se cuenta
     *   para la telemetría ([ConciliacionInversa.fotosSinEnviarViejas] y
     *   [ConciliacionInversa.fotoSinEnviarMasViejaMin]).
     * - **El precio:** una foto que el reenvío nunca logra estampar reabre su
     *   visita en cada corrida (un `POST` idempotente por corrida). Se ve en
     *   la telemetría: `fotos_sin_enviar_viejas` > 0 y la edad crece.
     * - **Un id que no es UUID canónico no se decide**, igual que en la parte
     *   by-ids: se queda como está.
     * - **El dinero va primero.** Con pagos o ventas por subir
     *   ([DineroPendiente]) no reabre nada: cada visita reabierta es un `POST`
     *   multipart por la misma red. Si no se puede saber, tampoco.
     * - **Válvula:** si ya lleva [POSPUESTAS_ANTES_DE_LA_VALVULA] corridas
     *   seguidas pospuestas por dinero, la siguiente corre igual con tope
     *   [MAX_DEVUELTAS_POR_VALVULA] y el contador vuelve a 0: un pago atorado
     *   no pospone el barrido para siempre (con dinero atorado, 2 visitas cada
     *   4 corridas).
     * - **Tope por pasada:** a lo más [MAX_DEVUELTAS_POR_PASADA] visitas, de la
     *   foto más vieja a la más nueva (por `CREADA_EN`; ilegible primero, luego
     *   id). El resto espera a la siguiente pasada; ninguna se abandona.
     */
    private suspend fun barrerFotosSinEnviar(ahora: Instant, cuenta: Contadores) {
        val fotos = attempt(ERROR_CODE_BARRIDO_ILEGIBLE, CONTEXT_BARRIDO) {
            store.fotosSinEnviarDeEntregadas()
        }?.filter { UUID_CANONICO.matches(it.visitaId) } ?: return
        if (fotos.isEmpty()) {
            anotarSeguidas(0)
            return
        }
        contarEdades(fotos, ahora, cuenta)
        val ids = deLaMasViejaALaMasNueva(fotos)
        var tope = MAX_DEVUELTAS_POR_PASADA
        if (hayDineroPendiente()) {
            val previas = leerSeguidas()
            if (previas < POSPUESTAS_ANTES_DE_LA_VALVULA) {
                cuenta.pospuesto = true
                cuenta.enEspera += ids.size
                cuenta.seguidas = previas + 1
                anotarSeguidas(cuenta.seguidas)
                errorReporter.report(
                    code = ERROR_CODE_BARRIDO_POSPUESTO_POR_DINERO,
                    message = "$CONTEXT_BARRIDO: hay pagos o ventas por subir; no se reabre nada",
                    props = mapOf(PROP_VISITAS to ids.size.toString())
                )
                return
            }
            // Válvula: un pago atorado no pospone el barrido para siempre.
            cuenta.porValvula = true
            tope = MAX_DEVUELTAS_POR_VALVULA
        }
        anotarSeguidas(0)
        val tanda = ids.take(tope)
        cuenta.enEspera += ids.size - tanda.size
        val devueltas = devolver(tanda, cuenta)
        if (devueltas > 0) {
            cuenta.devueltasPorFoto += devueltas
            errorReporter.report(
                code = ERROR_CODE_DEVUELTA_POR_FOTO,
                message = "$CONTEXT_BARRIDO: entregadas con una foto sin enviar vuelven a pendiente",
                props = mapOf(PROP_DEVUELTAS to devueltas.toString())
            )
        }
    }

    /** Una visita por foto más vieja; sin fecha legible va primero. Desempate por id. */
    private fun deLaMasViejaALaMasNueva(fotos: List<FotoSinEnviar>): List<String> =
        fotos.groupBy { it.visitaId }
            .map { (id, suyas) ->
                id to (suyas.mapNotNull { AppTime.parseWireFormatOrNull(it.creadaEn) }.minOrNull() ?: Instant.MIN)
            }
            .sortedWith(compareBy<Pair<String, Instant>> { it.second }.thenBy { it.first })
            .map { it.first }

    /** Válvula: una memoria ilegible cuenta como 0 (no abre la válvula a ciegas) y se reporta. */
    @Suppress("TooGenericExceptionCaught")
    private fun leerSeguidas(): Int = try {
        memoriaDePospuesto.seguidas()
    } catch (e: Exception) {
        errorReporter.report(
            ERROR_CODE_MEMORIA_DE_POSPUESTO,
            "$CONTEXT_BARRIDO: ${e::class.simpleName}",
            emptyMap()
        )
        0
    }

    @Suppress("TooGenericExceptionCaught")
    private fun anotarSeguidas(n: Int) {
        try {
            memoriaDePospuesto.anotar(n)
        } catch (e: Exception) {
            errorReporter.report(
                ERROR_CODE_MEMORIA_DE_POSPUESTO,
                "$CONTEXT_BARRIDO: ${e::class.simpleName}",
                emptyMap()
            )
        }
    }

    /** Si la lectura falla, se trata como "hay dinero": el barrido espera. */
    private suspend fun hayDineroPendiente(): Boolean =
        attempt(ERROR_CODE_DINERO_ILEGIBLE, CONTEXT_DINERO) { dineroPendiente.hayPendientes() } ?: true

    /** Solo telemetría (E-VIS-032): la edad de las fotos no decide nada. */
    private fun contarEdades(fotos: List<FotoSinEnviar>, ahora: Instant, cuenta: Contadores) {
        val limite = ReglaDePoda.limiteFotosSinEnviar(ahora)
        val creadas = fotos.map { AppTime.parseWireFormatOrNull(it.creadaEn) }
        val viejas = creadas.count { it == null || it.isBefore(limite) }
        cuenta.fotosSinEnviar += fotos.size
        cuenta.fotosSinEnviarViejas += viejas
        cuenta.fotoSinEnviarMasViejaMin = creadas.filterNotNull().minOrNull()?.let {
            Duration.between(it, ahora).toMinutes().coerceAtLeast(0L)
        }
        if (viejas > 0) {
            errorReporter.report(
                code = ERROR_CODE_FOTO_SIN_ENVIAR_VIEJA,
                message = "$CONTEXT_BARRIDO: fotos sin enviar con mas de una semana; se reenvian igual",
                props = mapOf(PROP_FOTOS to viejas.toString())
            )
        }
    }

    /**
     * Una tanda: si la consulta falla no decide nada (Review Focus 3); si
     * contesta, confirma lo nombrado de lo preguntado y devuelve el resto.
     */
    private suspend fun decidirTanda(
        tanda: List<String>,
        ahoraEnCable: String,
        cuenta: Contadores
    ) {
        val contestadas = attempt(
            ERROR_CODE_CONSULTA_FALLIDA,
            CONTEXT_CONSULTA,
            mapOf(PROP_TAMANO_LOTE to tanda.size.toString())
        ) { registry.findExisting(tanda) }
        if (contestadas == null) {
            cuenta.fallidas++
            return
        }
        val preguntadas = tanda.toSet()
        val ajenas = contestadas.filterNot { it in preguntadas }
        if (ajenas.isNotEmpty()) {
            cuenta.ajenos += ajenas.size
            errorReporter.report(
                code = ERROR_CODE_RESPUESTA_AJENA,
                message = "$CONTEXT_CONSULTA: la respuesta trae ids que no se preguntaron",
                props = mapOf(
                    PROP_TAMANO_LOTE to tanda.size.toString(),
                    PROP_DESCARTADOS to ajenas.size.toString()
                )
            )
        }
        val existentes = contestadas.filter { it in preguntadas }.toSet()
        val si = tanda.filter { it in existentes }
        val no = tanda.filterNot { it in existentes }
        cuenta.confirmadas += confirmar(si, ahoraEnCable, cuenta)
        cuenta.faltantes += devolver(no, cuenta)
    }

    /** Acumuladores de una corrida (Tarea 14: cada código reportado también se cuenta). */
    private class Contadores {
        var faltantes = 0
        var confirmadas = 0
        var fallidas = 0
        var ajenos = 0
        var retenidas = 0
        var confirmarFallido = 0
        var devolverFallido = 0
        var devueltasPorFoto = 0
        var fotosSinEnviar = 0
        var fotosSinEnviarViejas = 0
        var fotoSinEnviarMasViejaMin: Long? = null
        var pospuesto = false
        var enEspera = 0
        var seguidas = 0
        var porValvula = false
    }

    /** Ver el KDoc de clase: un id sin forma de UUID tumbaría su tanda entera con un 422. */
    private fun separarInvalidos(ids: List<String>): Pair<List<String>, List<String>> {
        val (validos, invalidos) = ids.partition { UUID_CANONICO.matches(it) }
        if (invalidos.isNotEmpty()) {
            errorReporter.report(
                code = ERROR_CODE_ID_INVALIDO,
                message = "$CONTEXT_LEER: ids sin forma de uuid; no se preguntan ni se deciden",
                props = mapOf(PROP_DESCARTADOS to invalidos.size.toString())
            )
        }
        return validos to invalidos
    }

    private suspend fun confirmar(ids: List<String>, ahora: String, cuenta: Contadores): Int {
        if (ids.isEmpty()) return 0
        val cambiadas = attempt(
            ERROR_CODE_CONFIRMAR_FALLIDO,
            CONTEXT_CONFIRMAR,
            mapOf(PROP_TAMANO_LOTE to ids.size.toString())
        ) { store.confirmar(ids, ahora) }
        if (cambiadas == null) {
            cuenta.confirmarFallido++
            return 0
        }
        if (cambiadas < ids.size) {
            cuenta.retenidas += ids.size - cambiadas
            errorReporter.report(
                code = ERROR_CODE_RETENIDAS_POR_COMPROBANTE,
                message = "$CONTEXT_CONFIRMAR: la retencion por foto dejo algunas sin confirmar",
                props = mapOf(
                    PROP_TAMANO_LOTE to ids.size.toString(),
                    PROP_RETENIDAS to (ids.size - cambiadas).toString()
                )
            )
        }
        return cambiadas
    }

    private suspend fun devolver(ids: List<String>, cuenta: Contadores): Int {
        if (ids.isEmpty()) return 0
        val devueltas = attempt(
            ERROR_CODE_DEVOLVER_FALLIDO,
            CONTEXT_DEVOLVER,
            mapOf(PROP_TAMANO_LOTE to ids.size.toString())
        ) { store.devolverAPendiente(ids) }
        if (devueltas == null) cuenta.devolverFallido++
        return devueltas ?: 0
    }

    /** Igual que en [ReconcileVisitsUseCase]: reporta todo y relanza la cancelación. */
    @Suppress("TooGenericExceptionCaught") // la norma de errores exige reportar TODO throwable.
    private suspend fun <T> attempt(
        code: String,
        context: String,
        props: Map<String, String> = emptyMap(),
        block: suspend () -> T
    ): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        errorReporter.report(
            code = code,
            message = "$context: ${throwable::class.simpleName}",
            props = props
        )
        null
    }

    companion object {
        /**
         * DA-4: la misma ventana que la retención de confirmadas. Una confirmada
         * se pregunta mientras el teléfono la conserva; las legadas sin
         * `CONFIRMADA_EN` se preguntan siempre.
         */
        const val VENTANA_DIAS: Int = ReglaDePoda.CONFIRMADAS_DIAS

        /** Ids leídos que no son UUID canónicos: se quedan como están (vivacidad de las demás). */
        const val ERROR_CODE_ID_INVALIDO: String = "visitas_reconcilia_id_invalido"

        private val UUID_CANONICO =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        const val ERROR_CODE_ENTREGADAS_ILEGIBLES: String = "visitas_reconcilia_entregadas_ilegibles"
        const val ERROR_CODE_CONSULTA_FALLIDA: String = "visitas_reconcilia_consulta_fallida"
        const val ERROR_CODE_RESPUESTA_AJENA: String = "visitas_reconcilia_respuesta_ajena"
        const val ERROR_CODE_CONFIRMAR_FALLIDO: String = "visitas_reconcilia_confirmar_fallido"
        const val ERROR_CODE_DEVOLVER_FALLIDO: String = "visitas_reconcilia_devolver_fallido"
        const val ERROR_CODE_RETENIDAS_POR_COMPROBANTE: String =
            "visitas_reconcilia_retenidas_por_comprobante"

        /** Alerta del spec §12-1 (`visitas.reconcilia.devuelta_a_pendiente` > 0). */
        const val ERROR_CODE_DEVUELTA_A_PENDIENTE: String = "visitas_reconcilia_devuelta_a_pendiente"

        /** Barrido (E-APP-079, E-VIS-031): entregadas devueltas a pendiente por una foto sin enviar. */
        const val ERROR_CODE_DEVUELTA_POR_FOTO: String = "visitas_reconcilia_devuelta_por_foto"

        /**
         * Barrido (E-VIS-032): fotos sin enviar con más de una semana según el
         * reloj del teléfono. Solo alerta: se reenvían igual. Reemplaza a
         * `visitas_reconcilia_foto_abandonada` (ya nada se abandona).
         */
        const val ERROR_CODE_FOTO_SIN_ENVIAR_VIEJA: String = "visitas_reconcilia_foto_sin_enviar_vieja"

        /**
         * Tope de visitas que el barrido reabre por pasada. En la simulación
         * de E-APP-079 (500 semillas × 200 acciones) ninguna pasada reabrió
         * más de 2 (57 pasadas con 1, 2 con 2): 10 cubre ese caso en una sola
         * pasada con 5× de margen, y acota a 10 `POST` multipart por pasada un
         * residuo grande, que se vacía en varias pasadas.
         */
        const val MAX_DEVUELTAS_POR_PASADA: Int = 10

        /**
         * Válvula: con esta cantidad de corridas seguidas ya pospuestas por
         * dinero, la siguiente corre igual, con tope [MAX_DEVUELTAS_POR_VALVULA].
         */
        const val POSPUESTAS_ANTES_DE_LA_VALVULA: Int = 3

        /** Válvula: visitas que reabre la corrida que pasa por encima del dinero. */
        const val MAX_DEVUELTAS_POR_VALVULA: Int = 2

        /** Válvula: no se pudo leer o anotar la memoria de corridas pospuestas. */
        const val ERROR_CODE_MEMORIA_DE_POSPUESTO: String = "visitas_reconcilia_memoria_de_pospuesto"

        /** Barrido: hay pagos o ventas por subir; esta pasada no reabre nada. */
        const val ERROR_CODE_BARRIDO_POSPUESTO_POR_DINERO: String = "visitas_reconcilia_barrido_pospuesto_por_dinero"

        /** Barrido: no se pudo saber si hay dinero por subir; se pospone. */
        const val ERROR_CODE_DINERO_ILEGIBLE: String = "visitas_reconcilia_dinero_ilegible"

        /** Barrido (E-APP-079): la lectura de fotos sin enviar falló; no se decidió nada. */
        const val ERROR_CODE_BARRIDO_ILEGIBLE: String = "visitas_reconcilia_barrido_ilegible"

        private const val CONTEXT_BARRIDO = "ReconcileDeliveredVisitsUseCase.fotosSinEnviarDeEntregadas"
        private const val CONTEXT_DINERO = "ReconcileDeliveredVisitsUseCase.dineroPendiente"
        private const val CONTEXT_LEER = "ReconcileDeliveredVisitsUseCase.entregadasDesde"
        private const val CONTEXT_CONSULTA = "ReconcileDeliveredVisitsUseCase.findExisting"
        private const val CONTEXT_CONFIRMAR = "ReconcileDeliveredVisitsUseCase.confirmar"
        private const val CONTEXT_DEVOLVER = "ReconcileDeliveredVisitsUseCase.devolverAPendiente"

        private const val PROP_TAMANO_LOTE = "tamano_lote"
        private const val PROP_DESCARTADOS = "descartados"
        private const val PROP_RETENIDAS = "retenidas"
        private const val PROP_DEVUELTAS = "devueltas"
        private const val PROP_REVISADAS = "revisadas"
        private const val PROP_FOTOS = "fotos"
        private const val PROP_VISITAS = "visitas"
    }
}
