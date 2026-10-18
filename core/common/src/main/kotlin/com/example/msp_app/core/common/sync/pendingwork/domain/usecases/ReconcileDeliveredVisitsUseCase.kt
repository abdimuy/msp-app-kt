package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.DeliveredVisitsStore
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
    val devolucionesFallidas: Int = 0
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
class ReconcileDeliveredVisitsUseCase(
    private val store: DeliveredVisitsStore,
    private val registry: VisitCustodyRegistry,
    private val errorReporter: SyncErrorReporter,
    private val clock: () -> Instant
) {

    suspend fun execute(dias: Int = VENTANA_DIAS): ConciliacionInversa {
        val ahora = clock()
        val desde = AppTime.toWireFormat(ahora.minus(Duration.ofDays(dias.toLong())))
        val ahoraEnCable = AppTime.toWireFormat(ahora)

        val ids = attempt(ERROR_CODE_ENTREGADAS_ILEGIBLES, CONTEXT_LEER) {
            store.entregadasDesde(desde)
        }?.distinct() ?: return ConciliacionInversa(0, 0, 0, 0, entregadasIlegibles = true)

        val (validos, invalidos) = separarInvalidos(ids)

        val cuenta = Contadores()

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
            devolucionesFallidas = cuenta.devolverFallido
        )
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

        private const val CONTEXT_LEER = "ReconcileDeliveredVisitsUseCase.entregadasDesde"
        private const val CONTEXT_CONSULTA = "ReconcileDeliveredVisitsUseCase.findExisting"
        private const val CONTEXT_CONFIRMAR = "ReconcileDeliveredVisitsUseCase.confirmar"
        private const val CONTEXT_DEVOLVER = "ReconcileDeliveredVisitsUseCase.devolverAPendiente"

        private const val PROP_TAMANO_LOTE = "tamano_lote"
        private const val PROP_DESCARTADOS = "descartados"
        private const val PROP_RETENIDAS = "retenidas"
        private const val PROP_DEVUELTAS = "devueltas"
        private const val PROP_REVISADAS = "revisadas"
    }
}
