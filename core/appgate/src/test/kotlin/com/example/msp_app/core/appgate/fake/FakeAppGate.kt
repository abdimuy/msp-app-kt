package com.example.msp_app.core.appgate.fake

import com.example.msp_app.core.appgate.MinVersionConfig
import com.example.msp_app.core.appgate.MinVersionConfigSource
import com.example.msp_app.core.appgate.VersionGateCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Caché en memoria. Fake, no mock: el repo no usa MockK — los dobles son
 * implementaciones reales y pequeñas del puerto.
 */
class FakeVersionGateCache(initial: MinVersionConfig = MinVersionConfig()) : VersionGateCache {
    private val state = MutableStateFlow(initial)

    var saveCount: Int = 0
        private set

    override val config: Flow<MinVersionConfig> = state

    override suspend fun save(config: MinVersionConfig) {
        saveCount++
        state.value = config
    }
}

/**
 * Fuente remota guionizada. Sin emisiones = "sin señal", que es el caso que
 * más importa probar.
 */
class FakeMinVersionConfigSource : MinVersionConfigSource {
    private val emissions = MutableSharedFlow<MinVersionConfig>(replay = 1)

    override fun observe(): Flow<MinVersionConfig> = emissions

    suspend fun emit(config: MinVersionConfig) {
        emissions.emit(config)
    }
}
