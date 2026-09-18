package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.CargarBitacoraDelCliente
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
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

/** Estado observable de la bitácora. */
@Immutable
data class BitacoraUiState(
    val cargando: Boolean = true,
    val bitacora: BitacoraCompleta? = null,
    val error: ErrorDeDetalle? = null,
    /** "Esconder cantidades" — la misma preferencia global que la lista y el detalle. */
    val montosOcultos: Boolean = false
)

/**
 * La bitácora completa como **destino propio**.
 *
 * Existe porque el "⋯" del detalle se fue. Ese botón apuntaba a la pantalla
 * legada y era, sin que se notara, el único camino a "ver los N contactos":
 * quitarlo sin darle casa a la bitácora habría borrado una función de verdad,
 * no un botón.
 *
 * `@HiltViewModel`, sin `@Singleton` (kill-switch de baseURL).
 */
@HiltViewModel
class BitacoraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cargarBitacoraDelCliente: CargarBitacoraDelCliente,
    private val privacidad: PrivacidadPort,
    private val tema: TemaDeLaAppPort,
    private val telemetry: Telemetry,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    /** El cliente cuya bitácora se abrió, leído del `SavedStateHandle`. */
    val clienteId: Int = checkNotNull(savedStateHandle.get<Int>(PagosRutas.ARG_CLIENTE_ID)) {
        "BitacoraViewModel sin ${PagosRutas.ARG_CLIENTE_ID} en el SavedStateHandle"
    }

    private val mutableState = MutableStateFlow(BitacoraUiState())

    /** La privacidad se DERIVA del puerto en cada emisión — ver `DetalleClienteViewModel.state`. */
    val state: StateFlow<BitacoraUiState> =
        combine(mutableState, privacidad.ocultos) { bitacora, ocultos ->
            bitacora.copy(montosOcultos = ocultos)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BitacoraUiState(montosOcultos = privacidad.ocultosAhora())
        )

    init {
        telemetry.screenView(PANTALLA)
        cargar()
    }

    /** Vuelve a leer. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = BitacoraUiState(cargando = true)
            mutableState.value = leer()
        }
    }

    /**
     * Alterna el tema **GLOBAL** de la app vía [TemaDeLaAppPort] —el mismo que
     * mueven la lista, el detalle, el cajón legado, Configuración y el reporte de
     * cobranza—, y por eso persiste: sobrevive a navegar y a que muera el
     * proceso. Calcado de `ListaDeClientesViewModel.alternarTema`.
     *
     * **Lo llama `MspThemeRevealHost`, no un botón de esta pantalla.** La
     * bitácora no pinta el glifo sol/luna todavía; instala el host para que el
     * mecanismo esté donde tiene que estar, y el host es quien pide el flip
     * justo después de grabar el frame viejo.
     *
     * No escribe estado aquí: esta pantalla no tiene `temaOscuro` en su
     * `UiState` porque no dibuja nada que dependa del tema vigente. El `alternar()`
     * real es síncrono (solo escribe `SharedPreferences`), así que no hace falta
     * lanzar una corrutina.
     */
    fun alternarTema() {
        telemetry.tap(PANTALLA, ACCION_TEMA)
        tema.alternar()
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier fallo de Room/Firestore degrada igual; se reporta.
    private suspend fun leer(): BitacoraUiState = try {
        val bitacora = withContext(io) { cargarBitacoraDelCliente(clienteId) }
        BitacoraUiState(
            cargando = false,
            bitacora = bitacora,
            error = if (bitacora == null) ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO else null
        )
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = PagosTelemetria.CODE_BITACORA_FALLO,
            message = "no se pudo armar la bitacora del cliente",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        BitacoraUiState(cargando = false, error = ErrorDeDetalle.FALLO_LA_CARGA)
    }

    private companion object {
        /** Id estático de pantalla para telemetría — sin PII. */
        const val PANTALLA = "pagos_bitacora"

        /** Id de la acción del tema en telemetría — mismo nombre que usa la lista. */
        const val ACCION_TEMA = "theme_toggle"
    }
}
