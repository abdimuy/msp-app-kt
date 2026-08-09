package com.example.msp_app.di

import com.example.msp_app.core.network.AuthTokenProvider
import com.example.msp_app.core.network.NetworkConfig
import com.example.msp_app.data.api.FirebaseAuthTokenProvider
import com.example.msp_app.data.api.appNetworkConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provee al grafo Hilt la [NetworkConfig] (del `BuildConfig` del flavor) y la
 * impl Firebase del puerto [AuthTokenProvider], las dos dependencias que necesita
 * el [com.example.msp_app.core.network.RetrofitClientFactory] de `:core:network`.
 *
 * Vive en `:app` (no en `:core:network`) porque `BuildConfig` es del módulo de
 * aplicación — un módulo *library* no puede leerlo. Los `object` legacy de red
 * ([com.example.msp_app.data.api.ApiProvider], etc.) arman su propio factory con
 * estas mismas dependencias vía `appNetworkConfig()`; este módulo las expone para
 * cualquier consumidor Hilt futuro (features nuevos que inyecten el factory).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkConfigModule {

    /**
     * [NetworkConfig] es data **inmutable** derivada del `BuildConfig` — NO un
     * servicio del kill-switch (ese vive en `ApiProvider`, que resuelve la URL
     * vigente por request). Por eso `@Singleton` es seguro y correcto: evita
     * releer `BuildConfig` en cada punto de inyección. La regla de "sin
     * `@Singleton`" aplica a los **servicios** producidos por
     * `ApiProvider.create(...)`, no a esta config.
     */
    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig = appNetworkConfig()

    /**
     * Impl Firebase del puerto de token bearer (caché de ~50 min). No sostiene
     * ningún `Retrofit`/servicio del kill-switch — es la *fuente* del token — así
     * que `@Singleton` es seguro: comparte la caché entre consumidores Hilt
     * (Firebase ya cachea `getIdToken(false)` internamente de todos modos).
     */
    @Provides
    @Singleton
    fun provideAuthTokenProvider(): AuthTokenProvider = FirebaseAuthTokenProvider()
}
