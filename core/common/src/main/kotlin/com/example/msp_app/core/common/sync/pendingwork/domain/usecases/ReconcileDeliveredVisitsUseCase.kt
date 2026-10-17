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
 * @param confirmadas las que el servidor nombró y quedaron de verdad con
 *   `CONFIRMADA_EN`.
 * @param tandasFallidas consultas `by-ids` que no contestaron: sus ids no
 *   cambiaron.
 * @param entregadasIlegibles la lectura local falló: no se preguntó nada.
 */
data class ConciliacionInversa(
    val revisadas: Int,
    val faltantes: Int,
    val confirmadas: Int,
    val tandasFallidas: Int,
    val entregadasIlegibles: Boolean = false
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
 * - **Un id que no se preguntó no se confirma**: se descarta y se reporta.
 * - **La comparación de ids es exacta**, igual que `existenciaCompleta`
 *   (Tarea 9). El servidor contesta `uuid.String()` (minúsculas canónicas) y
 *   el teléfono acuña `UUID.toString()` (también minúsculas): coinciden. Si
 *   algún día no, el error cae del lado seguro: la visita vuelve a pendiente y
 *   el servidor la deduplica al reenviarla; nunca se confirma sin prueba.
 * - Devolver a pendiente nunca pierde datos: lo peor que cuesta es un reenvío.
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

        var faltantes = 0
        var confirmadas = 0
        var fallidas = 0

        ids.chunked(VisitCustodyRegistry.MAX_IDS_PER_REQUEST).forEach { tanda ->
            val contestadas = attempt(
                ERROR_CODE_CONSULTA_FALLIDA,
                CONTEXT_CONSULTA,
                mapOf(PROP_TAMANO_LOTE to tanda.size.toString())
            ) { registry.findExisting(tanda) }
            if (contestadas == null) {
                fallidas++
                return@forEach
            }
            val preguntadas = tanda.toSet()
            val ajenas = contestadas.filterNot { it in preguntadas }
            if (ajenas.isNotEmpty()) {
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
            confirmadas += confirmar(si, ahoraEnCable)
            faltantes += devolver(no)
        }

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
        return ConciliacionInversa(ids.size, faltantes, confirmadas, fallidas)
    }

    private suspend fun confirmar(ids: List<String>, ahora: String): Int {
        if (ids.isEmpty()) return 0
        val cambiadas = attempt(
            ERROR_CODE_CONFIRMAR_FALLIDO,
            CONTEXT_CONFIRMAR,
            mapOf(PROP_TAMANO_LOTE to ids.size.toString())
        ) { store.confirmar(ids, ahora) } ?: return 0
        if (cambiadas < ids.size) {
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

    private suspend fun devolver(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return attempt(
            ERROR_CODE_DEVOLVER_FALLIDO,
            CONTEXT_DEVOLVER,
            mapOf(PROP_TAMANO_LOTE to ids.size.toString())
        ) { store.devolverAPendiente(ids) } ?: 0
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
