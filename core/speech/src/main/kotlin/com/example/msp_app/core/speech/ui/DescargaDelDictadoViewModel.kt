package com.example.msp_app.core.speech.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.speech.domain.port.ModeloDeDictadoPort
import com.example.msp_app.core.speech.domain.port.TemaDeLaAppPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * **El estado del modelo de voz para la UI.**
 *
 * Existe porque [DescargaDelDictadoScreen] es un Composable puro sobre
 * [EstadoDelModelo], y alguien tiene que leerlo del puerto. Sin esta pieza la
 * pantalla no se podía montar en el grafo — que es exactamente por lo que
 * estuvo construida y muerta.
 *
 * `@HiltViewModel`, **sin `@Singleton`** (kill-switch de baseURL): nada de lo
 * que inyecta sostiene un servicio de Retrofit. El estado durable vive en
 * `EstadoDelModeloEnMemoria`, del lado del adaptador, no acá.
 *
 * El `initialValue` es [EstadoDelModelo.Ausente] y no un "cargando": el puerto
 * contesta en el primer frame leyendo el archivo, y pintar un estado propio de
 * carga para eso metería un parpadeo donde no hay espera.
 */
@HiltViewModel
class DescargaDelDictadoViewModel @Inject constructor(
    private val modeloPort: ModeloDeDictadoPort,
    /**
     * El tema GLOBAL de la app. No lo pide un botón de esta pantalla —no pinta
     * el glifo sol/luna— sino `MspThemeRevealHost`, que instala
     * [DescargaDelDictadoScreen] y que necesita poder pedir el flip DESPUÉS de
     * haber grabado el frame viejo.
     */
    private val tema: TemaDeLaAppPort,
    /**
     * El paquete anunciado. Lo necesita la pantalla para decir **cuánto pesa**
     * antes de bajarlo, y sale del módulo —no de un número escrito a mano.
     */
    val modelo: ModeloDeDictado
) : ViewModel() {

    val estado: StateFlow<EstadoDelModelo> = modeloPort.estado().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = EstadoDelModelo.Ausente
    )

    fun descargar() {
        viewModelScope.launch { modeloPort.pedirLaDescarga() }
    }

    fun borrar() {
        viewModelScope.launch { modeloPort.cancelarYBorrar() }
    }

    /**
     * Alterna el tema **GLOBAL** de la app vía [TemaDeLaAppPort] —el mismo que
     * mueven la lista de clientes, el cajón legado, Configuración y el reporte de
     * cobranza—, y por eso persiste: sobrevive a navegar y a que muera el
     * proceso. Calcado de `feature.pagos.ui.ListaDeClientesViewModel.alternarTema`.
     *
     * **Sin telemetría, al revés que el calco.** Este ViewModel no inyecta
     * [com.example.msp_app.core.telemetry.Telemetry] y el módulo no emite ni
     * siquiera `screenView` para esta pantalla: agregar la dependencia entera
     * para un solo `tap` sería inventar el primer evento de UI de `:core:speech`
     * de paso, en un cambio que es de animación. Cuando la pantalla pinte el
     * glifo y el tap sea una acción real del cobrador, se agrega entonces.
     *
     * Ni corrutina ni estado: el `alternar()` real es síncrono (solo escribe
     * `SharedPreferences`) y esta pantalla no dibuja nada que dependa del tema
     * vigente — el color lo resuelve `MspTheme` leyendo `LocalAppDarkTheme`.
     */
    fun alternarTema() {
        tema.alternar()
    }
}
