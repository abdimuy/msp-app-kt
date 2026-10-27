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
    /**
     * La venta a la que lleva el aviso: la del abono sin guardar o la del pago o
     * condonación que no coincide, el más viejo; `null` si no hay.
     */
    val ventaId: Int?,
    /**
     * E-APP-118: cuántos envíos (abonos y condonaciones) no coinciden con la
     * oficina. Con uno o más, el aviso dice "Un envío no coincide con la
     * oficina" (decisión 4 del dueño 2026-10-20).
     */
    val noCoinciden: Int = 0
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
