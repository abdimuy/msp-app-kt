package com.example.msp_app.feature.garantias.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.feature.garantias.application.EntregarGarantia
import com.example.msp_app.feature.garantias.application.FalloDeCaptura
import com.example.msp_app.feature.garantias.application.ResultadoDeCaptura
import com.example.msp_app.feature.garantias.di.GarantiasIoDispatcher
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.example.msp_app.feature.garantias.domain.port.UbicacionDeGarantiaPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lo que pinta la entrega con firma.
 *
 * [ubicacion] es el GPS del cobrador y es **obligatorio** para entregar: el
 * servidor rechaza la entrega sin coordenadas (D5). Mientras no haya, la
 * pantalla apaga el botón con "Esperando ubicación" y ofrece reintentar.
 *
 * La firma **no** vive en el estado: el PNG se pide en el momento de guardar
 * ([EntregaViewModel.guardar]) desde el pad que la ruta posee. Guardarla acá
 * obligaría a serializar bytes que no sobreviven a una muerte de proceso de
 * todos modos.
 */
data class EntregaUiState(
    val ubicacion: Ubicacion? = null,
    val pidiendoUbicacion: Boolean = true,
    val guardando: Boolean = false,
    val fallo: FalloDeCaptura? = null,
    val falloTecnico: Boolean = false,
    val guardado: Boolean = false
)

/**
 * La entrega de un folio: GPS obligatorio y firma del cliente.
 *
 * La referencia local llega por [GarantiasV2Rutas.ARG_FOLIO] y vive en el
 * `SavedStateHandle`: la cámara y el GPS pueden matar el proceso, y un destino
 * tiene que volver con su argumento intacto.
 *
 * [pedirUbicacion] corre en el `init`: la pantalla abre pidiendo el GPS para no
 * hacer que el cobrador toque un botón antes de poder firmar. Un proveedor que
 * no contesta deja [EntregaUiState.ubicacion] en `null` y el botón de reintento
 * disponible, en vez de un callejón sin salida.
 */
@HiltViewModel
class EntregaViewModel @Inject constructor(
    private val entregar: EntregarGarantia,
    private val ubicacionPort: UbicacionDeGarantiaPort,
    savedStateHandle: SavedStateHandle,
    @GarantiasIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    /** La referencia **local** del folio que se está entregando. */
    val folioRef: String = checkNotNull(savedStateHandle.get<String>(GarantiasV2Rutas.ARG_FOLIO)) {
        "EntregaViewModel sin ${GarantiasV2Rutas.ARG_FOLIO} en el SavedStateHandle"
    }

    private val mutable = MutableStateFlow(EntregaUiState())
    val state: StateFlow<EntregaUiState> = mutable.asStateFlow()

    /** Guarda si hay una lectura de GPS en vuelo, aparte del estado que se pinta. */
    private var pidiendo = false

    init {
        pedirUbicacion()
    }

    /** Pide el GPS una vez; un proveedor que no contesta deja la ubicación en `null`. */
    fun pedirUbicacion() {
        if (pidiendo) return
        pidiendo = true
        mutable.value = mutable.value.copy(pidiendoUbicacion = true, fallo = null)
        viewModelScope.launch {
            val ubicacion = ubicacionPort.ubicacionActual()
            pidiendo = false
            mutable.value = mutable.value.copy(pidiendoUbicacion = false, ubicacion = ubicacion)
        }
    }

    /**
     * Encola la entrega con la firma dibujada.
     *
     * El caso de uso vuelve a evaluar el GPS y la firma antes de escribir; acá
     * sólo se traduce el resultado a estado. Una base que no abre no puede dejar
     * la pantalla colgada con "Guardando": se anota como fallo técnico.
     */
    @Suppress("TooGenericExceptionCaught") // escribir la cola no puede colgar la entrega.
    fun guardar(png: ByteArray?) {
        val actual = mutable.value
        if (actual.guardando) return
        mutable.value = actual.copy(guardando = true, fallo = null, falloTecnico = false)
        viewModelScope.launch {
            val resultado = try {
                withContext(io) { entregar.ejecutar(folioRef, actual.ubicacion, png) }
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(guardando = false, falloTecnico = true)
                return@launch
            }
            mutable.value = when (resultado) {
                is ResultadoDeCaptura.Encolado ->
                    mutable.value.copy(guardando = false, guardado = true)
                is ResultadoDeCaptura.Bloqueado ->
                    mutable.value.copy(guardando = false, fallo = resultado.fallo)
            }
        }
    }

    /** La ruta ya volvió al detalle; se suelta la señal para no repetirla. */
    fun guardadoNavegado() {
        mutable.value = mutable.value.copy(guardado = false)
    }
}
