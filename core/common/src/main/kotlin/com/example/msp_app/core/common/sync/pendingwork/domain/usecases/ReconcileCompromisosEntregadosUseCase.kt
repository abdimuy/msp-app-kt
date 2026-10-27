package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.CompromisoEnServidorRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.CompromisosEntregadosStore
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.visitas.envio.BrechaDeCompromiso
import com.example.msp_app.core.common.visitas.envio.brechaDeCompromiso
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

/**
 * Resultado de una corrida de [ReconcileCompromisosEntregadosUseCase].
 *
 * @param revisadas visitas con compromiso entregado que se preguntaron al servidor.
 * @param incompletas las que el servidor tiene pero con el compromiso sin completar
 *   ([porRellenar] + [distintas]); es la alerta `visitas.reconcilia.incompleta`.
 * @param porRellenar al servidor le faltan bloques (nulos) que el teléfono tiene:
 *   se devuelven a pendiente y el reenvío los rellena (spec §5.2.3).
 * @param distintas el servidor tiene OTRO compromiso: solo se reporta, NO se
 *   reenvía (un reenvío distinto responde 422 `visita_compromiso_distinto`; decisión
 *   del dueño 2026-10-27).
 * @param devueltas las [porRellenar] que de verdad volvieron a pendiente.
 * @param tandasFallidas consultas que no contestaron: sus ids no cambiaron.
 */
data class ConciliacionDeCompromisos(
    val revisadas: Int,
    val incompletas: Int,
    val porRellenar: Int,
    val distintas: Int,
    val devueltas: Int,
    val tandasFallidas: Int,
    val ilegibles: Boolean = false,
    val devolucionesFallidas: Int = 0
)

/**
 * Conciliación inversa del compromiso (plan visitas P2, Tarea 18; spec §4.6-1:
 * "si está pero con compromiso distinto ... se reporta
 * `visitas.reconcilia.incompleta` y se reenvía"; E-APP-132).
 *
 * Para cada visita soltada cuyo compromiso el teléfono da por entregado,
 * pregunta al servidor (`by-ids?detalle=1`) y compara con [brechaDeCompromiso]:
 *
 * - **COMPLETO**: nada.
 * - **POR_RELLENAR** (al servidor le falta un bloque que el teléfono tiene y
 *   ningún bloque difiere): se reporta y se devuelve a pendiente; el reenvío
 *   con el mismo id lo rellena (el servidor rellena nulos, nunca pisa).
 * - **DISTINTO** (algún bloque difiere): SOLO se reporta. No se reenvía ni se
 *   toca la visita: ese reenvío es el 422 `visita_compromiso_distinto`, que no
 *   arregla nada.
 *
 * ## Reglas de seguridad
 *
 * - **Una tanda que falla no decide nada** (mismo criterio que
 *   [ReconcileDeliveredVisitsUseCase]).
 * - **Una visita que el servidor no nombra no se toca aquí**: de la faltante se
 *   ocupa la inversa de la visita.
 * - **Lo que se decide sale de lo preguntado**, nunca de lo que la respuesta
 *   traiga de más.
 * - **No escribe en ninguna bandeja de dinero**; solo la tabla `Visit` vía
 *   [CompromisosEntregadosStore.devolverAPendiente] (E-VIS-116).
 * - Devolver a pendiente nunca pierde datos: lo peor que cuesta es un reenvío,
 *   y el tope es uno por visita por corrida diaria.
 */
class ReconcileCompromisosEntregadosUseCase(
    private val store: CompromisosEntregadosStore,
    private val registry: CompromisoEnServidorRegistry,
    private val errorReporter: SyncErrorReporter,
    private val clock: () -> Instant
) {

    suspend fun execute(
        dias: Int = ReconcileDeliveredVisitsUseCase.VENTANA_DIAS
    ): ConciliacionDeCompromisos {
        val desde = AppTime.toWireFormat(clock().minus(Duration.ofDays(dias.toLong())))
        val locales = attempt(ERROR_CODE_ILEGIBLES, CONTEXT_LEER) {
            store.entregadasConCompromisoDesde(desde)
        }?.distinctBy { it.visitaId } ?: return ConciliacionDeCompromisos(
            0, 0, 0, 0, 0, 0, ilegibles = true
        )

        val (validos, invalidos) = locales.partition { UUID_CANONICO.matches(it.visitaId) }
        if (invalidos.isNotEmpty()) {
            errorReporter.report(
                code = ERROR_CODE_ID_INVALIDO,
                message = "$CONTEXT_LEER: ids sin forma de uuid; no se preguntan ni se deciden",
                props = mapOf(PROP_DESCARTADOS to invalidos.size.toString())
            )
        }

        var revisadas = 0
        var porRellenar = 0
        var distintas = 0
        var devueltas = 0
        var fallidas = 0
        var devolucionesFallidas = 0
        validos.chunked(CompromisoEnServidorRegistry.MAX_IDS_PER_REQUEST).forEach { tanda ->
            val ids = tanda.map { it.visitaId }
            val contestadas = attempt(
                ERROR_CODE_CONSULTA_FALLIDA,
                CONTEXT_CONSULTA,
                mapOf(PROP_TAMANO_LOTE to ids.size.toString())
            ) { registry.findCompromisos(ids) }
            if (contestadas == null) {
                fallidas++
                return@forEach
            }
            val delServidor = contestadas.filter { it.visitaId in ids }.associateBy { it.visitaId }
            val rellenar = mutableListOf<String>()
            tanda.forEach { local ->
                val servidor = delServidor[local.visitaId] ?: return@forEach
                revisadas++
                when (brechaDeCompromiso(local.compromiso, servidor.compromiso)) {
                    BrechaDeCompromiso.COMPLETO -> Unit
                    BrechaDeCompromiso.POR_RELLENAR -> {
                        porRellenar++
                        rellenar += local.visitaId
                    }
                    BrechaDeCompromiso.DISTINTO -> distintas++
                }
            }
            if (rellenar.isNotEmpty()) {
                val cambiadas = attempt(
                    ERROR_CODE_DEVOLVER_FALLIDO,
                    CONTEXT_DEVOLVER,
                    mapOf(PROP_TAMANO_LOTE to rellenar.size.toString())
                ) { store.devolverAPendiente(rellenar) }
                if (cambiadas == null) devolucionesFallidas++ else devueltas += cambiadas
            }
        }

        val incompletas = porRellenar + distintas
        if (incompletas > 0) {
            errorReporter.report(
                code = ERROR_CODE_INCOMPLETA,
                message = "$CONTEXT_CONSULTA: el servidor tiene visitas sin el compromiso completo",
                props = mapOf(
                    PROP_POR_RELLENAR to porRellenar.toString(),
                    PROP_DISTINTAS to distintas.toString(),
                    PROP_REVISADAS to revisadas.toString()
                )
            )
        }
        return ConciliacionDeCompromisos(
            revisadas = revisadas,
            incompletas = incompletas,
            porRellenar = porRellenar,
            distintas = distintas,
            devueltas = devueltas,
            tandasFallidas = fallidas,
            devolucionesFallidas = devolucionesFallidas
        )
    }

    /** Reporta todo y relanza la cancelación, igual que [ReconcileDeliveredVisitsUseCase]. */
    @Suppress("TooGenericExceptionCaught")
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
        /** Alerta del spec §4.6-1 (`visitas.reconcilia.incompleta` > 0). */
        const val ERROR_CODE_INCOMPLETA: String = "visitas_reconcilia_incompleta"
        const val ERROR_CODE_ILEGIBLES: String = "visitas_reconcilia_compromisos_ilegibles"
        const val ERROR_CODE_ID_INVALIDO: String = "visitas_reconcilia_compromisos_id_invalido"
        const val ERROR_CODE_CONSULTA_FALLIDA: String = "visitas_reconcilia_compromisos_consulta_fallida"
        const val ERROR_CODE_DEVOLVER_FALLIDO: String = "visitas_reconcilia_compromisos_devolver_fallido"

        private const val CONTEXT_LEER = "ReconcileCompromisosEntregadosUseCase.entregadasConCompromisoDesde"
        private const val CONTEXT_CONSULTA = "ReconcileCompromisosEntregadosUseCase.findCompromisos"
        private const val CONTEXT_DEVOLVER = "ReconcileCompromisosEntregadosUseCase.devolverAPendiente"
        private const val PROP_TAMANO_LOTE = "tamano_lote"
        private const val PROP_DESCARTADOS = "descartados"
        private const val PROP_POR_RELLENAR = "por_rellenar"
        private const val PROP_DISTINTAS = "distintas"
        private const val PROP_REVISADAS = "revisadas"

        private val UUID_CANONICO =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
