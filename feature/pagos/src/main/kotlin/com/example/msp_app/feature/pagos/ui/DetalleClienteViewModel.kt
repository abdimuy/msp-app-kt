package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.CargarDetalleCliente
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
 * El detalle de cliente como **destino de navegación con su propio ViewModel y
 * `SavedStateHandle`** — no un diálogo.
 *
 * No es cosmético y es el patrón que fijan las Tasks 17-21: un
 * `FullScreenDialog` vive dentro de la composición de quien lo abrió y muere
 * con ella; la cámara (Tasks 22-23) saca al usuario de la app y el proceso
 * puede morir mientras tanto. Un destino con `SavedStateHandle` sobrevive a
 * eso, un diálogo no.
 *
 * `@HiltViewModel`, sin `@Singleton` (kill-switch de baseURL): nada de lo que
 * inyecta sostiene una sesión o un API service.
 */
@HiltViewModel
class DetalleClienteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cargarDetalleCliente: CargarDetalleCliente,
    private val telemetry: Telemetry,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    /**
     * El cliente que se abrió, leído del `SavedStateHandle` — no de un campo
     * del ViewModel ni de un parámetro del Composable, que es lo que se pierde
     * cuando el proceso muere detrás de la cámara.
     */
    val clienteId: Int = checkNotNull(savedStateHandle.get<Int>(PagosRutas.ARG_CLIENTE_ID)) {
        "DetalleClienteViewModel sin ${PagosRutas.ARG_CLIENTE_ID} en el SavedStateHandle"
    }

    private val mutableState = MutableStateFlow(DetalleClienteUiState())
    val state: StateFlow<DetalleClienteUiState> = mutableState.asStateFlow()

    init {
        telemetry.screenView(PANTALLA)
        cargar()
    }

    /** Vuelve a leer. Cada llamada es UNA sincronización — ver [CargarDetalleCliente]. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = DetalleClienteUiState(cargando = true)
            mutableState.value = leer()
        }
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier fallo de Room/Firestore degrada igual; se reporta.
    private suspend fun leer(): DetalleClienteUiState = try {
        val detalle = withContext(io) { cargarDetalleCliente(clienteId) }
        DetalleClienteUiState(
            cargando = false,
            detalle = detalle,
            error = if (detalle == null) ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO else null
        )
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = PagosTelemetria.CODE_DETALLE_CLIENTE_FALLO,
            message = "no se pudo armar el detalle de cliente",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        DetalleClienteUiState(cargando = false, error = ErrorDeDetalle.FALLO_LA_CARGA)
    }

    private companion object {
        /** Id estático de pantalla para telemetría — sin PII. */
        const val PANTALLA = "pagos_detalle_cliente"
    }
}
