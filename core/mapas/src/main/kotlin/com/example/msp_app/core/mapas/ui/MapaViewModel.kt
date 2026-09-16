package com.example.msp_app.core.mapas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.mapas.application.MapasTelemetria
import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.mapas.domain.port.ExtractoDeMapaPort
import com.example.msp_app.core.telemetry.Telemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * El estado del extracto para la UI: lo lee el suelo del mapa y lo lee la
 * pantalla de descarga.
 *
 * **Uno solo para las dos**, y no es pereza: son la misma pregunta ("¿hay mapa
 * en este teléfono?") hecha desde dos lugares. Dos ViewModels sobre el mismo
 * puerto podrían contestar distinto durante una descarga, y entonces la pantalla
 * diría "listo" mientras el detalle del cliente sigue con el suelo liso.
 *
 * `@HiltViewModel`, **sin `@Singleton`** (kill-switch de baseURL): nada de lo
 * que inyecta sostiene un servicio de Retrofit, y el estado durable vive en
 * `EstadoDelExtractoEnMemoria`, no acá.
 */
@HiltViewModel
class MapaViewModel @Inject constructor(
    private val extracto: ExtractoDeMapaPort,
    private val telemetry: Telemetry,
    /**
     * El paquete anunciado, o `null` si no hay origen publicado. Lo necesita la
     * pantalla para decir **cuánto pesa** antes de bajarlo; el suelo no lo mira.
     */
    val paquete: ExtractoDeMapa?
) : ViewModel() {

    val estado: StateFlow<EstadoDelExtracto> = extracto.estado().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = EstadoDelExtracto.SinOrigen
    )

    fun descargar() {
        viewModelScope.launch { extracto.pedirLaDescarga() }
    }

    fun borrar() {
        viewModelScope.launch { extracto.cancelarYBorrar() }
    }

    /**
     * MapLibre no arrancó. Se reporta con el nombre de la clase de excepción y
     * **nada más**: ni la ruta del archivo (lleva el `filesDir`) ni, por
     * supuesto, el punto que se iba a dibujar.
     */
    fun motorNoArranco(fallo: Throwable) {
        telemetry.error(
            code = MapasTelemetria.CODE_MOTOR_NO_ARRANCO,
            message = "MapLibre no arranco; el bloque degrada a suelo liso",
            props = mapOf(MapasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
    }
}
