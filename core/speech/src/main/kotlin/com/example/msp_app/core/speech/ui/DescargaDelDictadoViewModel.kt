package com.example.msp_app.core.speech.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.speech.domain.port.ModeloDeDictadoPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * **El estado del modelo de voz para la UI.**
 *
 * Existe por la misma razón que `MapaViewModel` en `:core:mapas`:
 * [DescargaDelDictadoScreen] es un Composable puro sobre
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
}
