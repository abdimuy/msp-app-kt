package com.example.msp_app.feature.garantias.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.feature.garantias.application.FolioEnBandeja
import com.example.msp_app.feature.garantias.application.LeerMisFolios
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
 * Lo que pinta la bandeja de "mis folios".
 *
 * [cargando] empieza en `true`: la primera lectura es de Room y puede tardar un
 * instante; el cobrador ve "Cargando" y no una lista vacía que miente.
 * [fallo] sólo se levanta si la lectura misma reventó —una base que no abre—,
 * no cuando el resultado es una lista vacía, que es un estado legítimo.
 */
data class BandejaUiState(
    val cargando: Boolean = true,
    val folios: List<FolioEnBandeja> = emptyList(),
    val fallo: Boolean = false
)

/**
 * La bandeja de la captura: los folios que **este teléfono** abrió.
 *
 * La lectura es local a propósito (D5): un sótano sin señal no puede quedarse
 * con la pantalla en blanco. No hay `savedStateHandle` porque la bandeja no
 * recibe argumentos y su estado se reconstruye con una lectura.
 *
 * La primera lectura la dispara la ruta ([BandejaDeGarantiasContenido] no la
 * conoce) con un `LaunchedEffect`: la bandeja se vuelve a leer al volver del
 * detalle, así un folio recién abierto aparece sin reabrir la pantalla.
 */
@HiltViewModel
class BandejaDeGarantiasViewModel @Inject constructor(
    private val leerMisFolios: LeerMisFolios,
    @GarantiasIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val mutable = MutableStateFlow(BandejaUiState())
    val state: StateFlow<BandejaUiState> = mutable.asStateFlow()

    /**
     * Vuelve a leer la bandeja.
     *
     * Una base que no abre no puede tumbar la composición: la compuerta del
     * grafo de `:app` compone esta pantalla con Room vacío y sin señal, y un
     * `throw` acá dejaría la navegación entera en rojo.
     */
    @Suppress("TooGenericExceptionCaught") // leer la base no puede tumbar la bandeja.
    fun cargar() {
        mutable.value = mutable.value.copy(cargando = true, fallo = false)
        viewModelScope.launch {
            try {
                val folios = withContext(io) { leerMisFolios.ejecutar() }
                mutable.value = BandejaUiState(cargando = false, folios = folios)
            } catch (_: Exception) {
                mutable.value = BandejaUiState(cargando = false, fallo = true)
            }
        }
    }
}
