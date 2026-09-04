package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Immutable
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta

/**
 * Por qué no hay pantalla que pintar. Son dos ramas y no un `null`, por la
 * misma razón que `CycleStart` distingue "no hay dato" de "no se pudo leer":
 * al cobrador se le dice una cosa distinta en cada caso, y aplanarlas fue
 * exactamente el defecto D5.
 */
enum class ErrorDeDetalle {
    /** El teléfono no tiene ese cliente / esa venta. No se reintenta. */
    NO_ESTA_EN_EL_TELEFONO,

    /** La carga falló. Se puede reintentar. */
    FALLO_LA_CARGA
}

/** Estado observable del detalle de cliente. */
@Immutable
data class DetalleClienteUiState(
    val cargando: Boolean = true,
    val detalle: DetalleCliente? = null,
    val error: ErrorDeDetalle? = null
)

/** Estado observable del detalle de venta. */
@Immutable
data class DetalleVentaUiState(
    val cargando: Boolean = true,
    val detalle: DetalleVenta? = null,
    val error: ErrorDeDetalle? = null
)
