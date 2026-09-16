package com.example.msp_app.feature.configuracion.data.fake

import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.port.ExtractoDeMapaPort
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.port.ModeloDeDictadoPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fakes de los dos puertos de descarga: estado público + contadores públicos de
 * llamadas, escritos a mano. **Nunca MockK ni Mockito** (`global-constraints.md`).
 *
 * Son propios de este módulo y no un `import` de los de `:core:speech`/
 * `:core:mapas`: un source set `test` no cruza de módulo, y publicarlos a
 * `:core:testing` sólo para compartir treinta líneas ataría la infraestructura
 * de prueba del repo entero a dos puertos de dos módulos.
 */
class FakeModeloDeDictadoPort(
    inicial: EstadoDelModelo = EstadoDelModelo.Ausente
) : ModeloDeDictadoPort {

    private val flujo = MutableStateFlow(inicial)

    var descargasPedidas: Int = 0
        private set

    var borrados: Int = 0
        private set

    override fun estado(): Flow<EstadoDelModelo> = flujo

    override suspend fun pedirLaDescarga() {
        descargasPedidas += 1
    }

    override suspend fun cancelarYBorrar() {
        borrados += 1
    }

    /** Mueve el estado, como lo movería el worker real. */
    fun emite(estado: EstadoDelModelo) {
        flujo.value = estado
    }
}

/** El hermano del de arriba, para el extracto de mapa. */
class FakeExtractoDeMapaPort(
    inicial: EstadoDelExtracto = EstadoDelExtracto.SinOrigen
) : ExtractoDeMapaPort {

    private val flujo = MutableStateFlow(inicial)

    var descargasPedidas: Int = 0
        private set

    var borrados: Int = 0
        private set

    override fun estado(): Flow<EstadoDelExtracto> = flujo

    override suspend fun pedirLaDescarga() {
        descargasPedidas += 1
    }

    override suspend fun cancelarYBorrar() {
        borrados += 1
    }

    /** Mueve el estado, como lo movería el worker real. */
    fun emite(estado: EstadoDelExtracto) {
        flujo.value = estado
    }
}
