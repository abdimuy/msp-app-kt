package com.example.msp_app.feature.configuracion.data.fake

import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.port.ModeloDeDictadoPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake del puerto de descarga del dictado: estado público + contadores públicos
 * de llamadas, escrito a mano. **Nunca MockK ni Mockito**
 * (`global-constraints.md`).
 *
 * Es propio de este módulo y no un `import` del de `:core:speech`: un source set
 * `test` no cruza de módulo, y publicarlo a `:core:testing` sólo para compartir
 * treinta líneas ataría la infraestructura de prueba del repo entero al puerto
 * de un módulo.
 *
 * El archivo se llamaba `FakesDeLasDescargas` cuando además vivía acá el fake
 * del extracto de mapa; con `:core:mapas` fuera queda un solo tipo, y detekt
 * (`MatchingDeclarationName`) pide que el archivo se llame como él.
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
