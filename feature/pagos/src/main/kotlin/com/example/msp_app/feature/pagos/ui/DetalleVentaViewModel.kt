package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.port.PrivacidadPort
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val tema: TemaDeLaAppPort,
    private val privacidad: PrivacidadPort,
    private val telemetry: Telemetry,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    val ventaId: Int = checkNotNull(savedStateHandle.get<Int>(PagosRutas.ARG_VENTA_ID)) {
        "DetalleVentaViewModel sin ${PagosRutas.ARG_VENTA_ID} en el SavedStateHandle"
    }

    private val mutableState = MutableStateFlow(DetalleVentaUiState())

    /**
     * El detalle, con el tema y la privacidad **derivados** de sus puertos en
     * cada emisión — mismo reparto que `DetalleClienteViewModel.state` y por la
     * misma razón: `cargar()` construye un estado nuevo desde cero y pisaría el
     * ojo y el tema si vivieran en [mutableState]. El valor inicial se siembra
     * con las lecturas síncronas para no enseñar los montos un frame.
     */
    val state: StateFlow<DetalleVentaUiState> =
        combine(mutableState, tema.oscuro, privacidad.ocultos) { detalle, oscuro, ocultos ->
            detalle.copy(temaOscuro = oscuro, montosOcultos = ocultos)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DetalleVentaUiState(
                temaOscuro = tema.oscuroAhora(),
                montosOcultos = privacidad.ocultosAhora()
            )
        )

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

    /**
     * Vuelve a leer **sin parpadeo**: a diferencia de [cargar], no reemplaza el
     * estado por uno en blanco con `cargando = true`. La usa la pantalla al
     * reanudarse — ver `RecargaAlVolver` —, y un spinner de cuerpo entero al
     * volver de registrar un abono o una visita se lee como si la pantalla se
     * hubiera perdido.
     *
     * Sin nada que conservar entre lecturas: a diferencia de la bitácora, esta
     * pantalla ya no tiene filtro ni alcance en su `UiState` — ver el KDoc de
     * [DetalleVentaUiState].
     */
    fun recargar() {
        viewModelScope.launch {
            mutableState.value = leer()
        }
    }

    /**
     * Alterna el tema **GLOBAL** de la app vía [TemaDeLaAppPort] —el mismo que
     * mueven la lista y el detalle de cliente—. Lo llaman el sol/luna de esta
     * pantalla y `MspThemeRevealHost`; el glifo lo repinta la colecta de
     * [TemaDeLaAppPort.oscuro] que sostiene [state].
     */
    fun alternarTema() {
        telemetry.tap(PANTALLA, ACCION_TEMA)
        tema.alternar()
    }

    /**
     * Esconde o enseña los montos — la misma preferencia global que el ojo del
     * detalle de cliente. [PrivacidadPort.alternar] suspende (DataStore).
     */
    fun alternarPrivacidad() {
        telemetry.tap(PANTALLA, ACCION_PRIVACIDAD)
        viewModelScope.launch { privacidad.alternar() }
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

        /** Id de la acción del tema en telemetría — mismo nombre que usa la lista. */
        const val ACCION_TEMA = "theme_toggle"

        /** Id de la acción del ojo — mismo nombre que el detalle de cliente. */
        const val ACCION_PRIVACIDAD = "privacidad"
    }
}
