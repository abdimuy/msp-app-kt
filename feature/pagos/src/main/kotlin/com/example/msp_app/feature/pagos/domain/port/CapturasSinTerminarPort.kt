package com.example.msp_app.feature.pagos.domain.port

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Cuántas capturas del cobrador en sesión no terminaron su camino (E-APP-084):
 * CAPTURADA (sin guardar) o con discrepancia ("Un envío no coincide con la
 * oficina"). Nunca se borran solas (E-APP-090): se guardan o quedan aquí.
 */
data class CapturasSinTerminar(
    val cuantas: Int,
    /** La venta del abono sin guardar más viejo, para el enlace; `null` si no hay. */
    val ventaId: Int?
) {
    companion object {
        val NINGUNA = CapturasSinTerminar(cuantas = 0, ventaId = null)
    }
}

fun interface CapturasSinTerminarPort {
    fun observar(): Flow<CapturasSinTerminar>

    companion object {
        /** Para montar la lista en una prueba sin marcadores. */
        val NINGUNA = CapturasSinTerminarPort { flowOf(CapturasSinTerminar.NINGUNA) }
    }
}
