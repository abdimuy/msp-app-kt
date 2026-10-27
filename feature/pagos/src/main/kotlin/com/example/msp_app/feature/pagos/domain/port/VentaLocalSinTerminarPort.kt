package com.example.msp_app.feature.pagos.domain.port

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * E-APP-119 (D15 ampliado a la venta local, coordinador 2026-10-26): la venta
 * local que el vendedor capturó y que no terminó su camino. El toque del aviso
 * abre la venta si ya está escrita o el borrador si no.
 */
data class VentaLocalSinTerminar(val saleId: String, val yaSeGuardo: Boolean)

fun interface VentaLocalSinTerminarPort {
    fun observar(): Flow<VentaLocalSinTerminar?>

    companion object {
        /** Para montar la lista en una prueba sin ventas locales pendientes. */
        val NINGUNA = VentaLocalSinTerminarPort { flowOf(null) }
    }
}
