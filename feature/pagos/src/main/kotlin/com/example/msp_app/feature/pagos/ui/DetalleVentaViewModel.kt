package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * El detalle de venta, mismo patrón que [DetalleClienteViewModel]: destino de
 * navegación, `SavedStateHandle`, una carga por sincronización.
 *
 * Solo recibe el `ventaId`: desde un pago o un recibo se entra directo a su
 * venta (Task 21) y ahí nadie conoce al cliente.
 */
@HiltViewModel
class DetalleVentaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cargarDetalleVenta: CargarDetalleVenta,
    private val telemetry: Telemetry,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    val ventaId: Int = checkNotNull(savedStateHandle.get<Int>(PagosRutas.ARG_VENTA_ID)) {
        "DetalleVentaViewModel sin ${PagosRutas.ARG_VENTA_ID} en el SavedStateHandle"
    }

    private val mutableState = MutableStateFlow(DetalleVentaUiState())
    val state: StateFlow<DetalleVentaUiState> = mutableState.asStateFlow()

    init {
        telemetry.screenView(PANTALLA)
        cargar()
    }

    /** Vuelve a leer. Cada llamada es UNA sincronización. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = DetalleVentaUiState(cargando = true)
            mutableState.value = leer()
        }
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier fallo de Room/Firestore degrada igual; se reporta.
    private suspend fun leer(): DetalleVentaUiState = try {
        val detalle = withContext(io) { cargarDetalleVenta(ventaId) }
        DetalleVentaUiState(
            cargando = false,
            detalle = detalle,
            error = if (detalle == null) ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO else null
        )
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = PagosTelemetria.CODE_DETALLE_VENTA_FALLO,
            message = "no se pudo armar el detalle de venta",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        DetalleVentaUiState(cargando = false, error = ErrorDeDetalle.FALLO_LA_CARGA)
    }

    private companion object {
        const val PANTALLA = "pagos_detalle_venta"
    }
}
