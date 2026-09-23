package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
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
    private val tema: TemaDeLaAppPort,
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

    /** Cambia qué se enseña de la línea. No recarga: el filtro vive sobre lo cargado. */
    fun filtrar(filtro: FiltroDeContactos) {
        mutableState.value = mutableState.value.copy(filtro = filtro)
    }

    /** Angosta la línea a esta cuenta, o la abre al cliente entero. */
    fun alcance(soloEstaVenta: Boolean) {
        mutableState.value = mutableState.value.copy(soloEstaVenta = soloEstaVenta)
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
     * Conserva [DetalleVentaUiState.filtro] y [DetalleVentaUiState.soloEstaVenta]
     * explícitamente: [leer] arma un `DetalleVentaUiState` desde cero y, sin este
     * `copy`, una recarga en segundo plano tiraría al default la pastilla y el
     * alcance que el cobrador ya había elegido.
     */
    fun recargar() {
        viewModelScope.launch {
            val filtro = mutableState.value.filtro
            val soloEstaVenta = mutableState.value.soloEstaVenta
            mutableState.value = leer().copy(filtro = filtro, soloEstaVenta = soloEstaVenta)
        }
    }

    /**
     * Alterna el tema **GLOBAL** de la app vía [TemaDeLaAppPort] —el mismo que
     * mueven la lista, el detalle de cliente, el cajón legado, Configuración y el
     * reporte de cobranza—, y por eso persiste: sobrevive a navegar y a que muera
     * el proceso. Calcado de `ListaDeClientesViewModel.alternarTema`.
     *
     * **Lo llama `MspThemeRevealHost`, no un botón de esta pantalla.** El detalle
     * de venta no pinta el glifo sol/luna todavía; instala el host para que el
     * mecanismo esté donde tiene que estar, y el host es quien pide el flip justo
     * después de grabar el frame viejo.
     *
     * No escribe estado aquí: esta pantalla no tiene `temaOscuro` en su `UiState`
     * porque no dibuja nada que dependa del tema vigente. El `alternar()` real es
     * síncrono (solo escribe `SharedPreferences`), así que no hace falta lanzar
     * una corrutina.
     */
    fun alternarTema() {
        telemetry.tap(PANTALLA, ACCION_TEMA)
        tema.alternar()
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
    }
}
