package com.example.msp_app.feature.garantias.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.feature.garantias.application.DetalleDelFolio
import com.example.msp_app.feature.garantias.application.LeerDetalleDeFolio
import com.example.msp_app.feature.garantias.di.GarantiasIoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lo que pinta el detalle de un folio.
 *
 * [detalle] es `null` sólo mientras carga o cuando la lectura misma reventó; un
 * folio que no existe, uno sin señal y uno rechazado se pintan igual, porque
 * [LeerDetalleDeFolio] siempre devuelve la verdad local (D5).
 */
data class DetalleDeFolioUiState(
    val cargando: Boolean = true,
    val detalle: DetalleDelFolio? = null,
    val fallo: Boolean = false
)

/**
 * El detalle de un folio con su línea de tiempo.
 *
 * La referencia local llega por [GarantiasV2Rutas.ARG_FOLIO] y vive en el
 * `SavedStateHandle`: la cámara y el GPS pueden matar el proceso, y un destino
 * tiene que volver con su argumento intacto. No se reenvía la navegación a
 * parámetros del Composable porque el `NavHost` anidado ya la entrega por acá.
 */
@HiltViewModel
class DetalleDeFolioViewModel @Inject constructor(
    private val leerDetalle: LeerDetalleDeFolio,
    savedStateHandle: SavedStateHandle,
    @GarantiasIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    /** La referencia **local** del folio; es la llave de toda la pantalla. */
    val folioRef: String = checkNotNull(savedStateHandle.get<String>(GarantiasV2Rutas.ARG_FOLIO)) {
        "DetalleDeFolioViewModel sin ${GarantiasV2Rutas.ARG_FOLIO} en el SavedStateHandle"
    }

    private val mutable = MutableStateFlow(DetalleDeFolioUiState())
    val state: StateFlow<DetalleDeFolioUiState> = mutable.asStateFlow()

    /**
     * Vuelve a leer el folio.
     *
     * Una base que no abre no puede tumbar la composición: la compuerta del
     * grafo de `:app` compone esta pantalla con Room vacío y sin señal.
     */
    @Suppress("TooGenericExceptionCaught") // leer la base no puede tumbar el detalle.
    fun cargar() {
        mutable.value = mutable.value.copy(cargando = true, fallo = false)
        viewModelScope.launch {
            try {
                val detalle = withContext(io) { leerDetalle.ejecutar(folioRef) }
                mutable.value = DetalleDeFolioUiState(cargando = false, detalle = detalle)
            } catch (_: Exception) {
                mutable.value = DetalleDeFolioUiState(cargando = false, fallo = true)
            }
        }
    }
}
