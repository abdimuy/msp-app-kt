package com.example.msp_app.data.api

import com.example.msp_app.BuildConfig
import com.example.msp_app.core.network.NetworkConfig
import com.example.msp_app.core.network.RetrofitClientFactory
import com.example.msp_app.core.utils.Constants
import javax.inject.Provider

/**
 * Fuente única, para los `object` legacy de red de `:app`
 * ([ApiProvider]/[V2ApiProvider]/[ApiProviderImages]), de la [NetworkConfig]
 * derivada del `BuildConfig` del flavor y del [RetrofitClientFactory] de
 * `:core:network`.
 *
 * Esos `object` no pueden recibir inyección Hilt (son singletons estáticos que
 * ~27 llamadas legacy usan como `Foo.create(...)`), así que arman aquí el mismo
 * factory con las mismas dependencias que `NetworkConfigModule` provee al grafo
 * Hilt — una sola definición de las URLs y del `appVersion`, sin duplicar.
 *
 * **Kill-switch:** el factory NUNCA congela la baseURL; resuelve la config por
 * llamada vía `Provider<NetworkConfig>`. La reactividad de la baseURL v1 vive en
 * [ApiProvider] (listener Firestore que, en release, pasa la URL vigente al
 * factory en cada `create`). La v2 y la de imágenes no tienen kill-switch (URL
 * estática del flavor), igual que antes de T7.
 */
internal fun appNetworkConfig(): NetworkConfig = NetworkConfig(
    legacyBaseUrl = BuildConfig.LEGACY_BASE_URL,
    v2BaseUrl = BuildConfig.V2_BASE_URL,
    imagesBaseUrl = BuildConfig.IMAGES_BASE_URL,
    // Misma fuente que el resto de la app (telemetría/updates): el sufijo del
    // flavor (`-local+sha`) se recorta para que el header quede atribuible al SHA.
    appVersion = Constants.APP_VERSION
)

/**
 * Factory Retrofit compartido por los `object` legacy. `by lazy` para no tocar
 * Firebase en el classload de `:app`; la impl del token
 * ([FirebaseAuthTokenProvider]) solo llama a Firebase al resolver un token.
 */
internal val appRetrofitClientFactory: RetrofitClientFactory by lazy {
    RetrofitClientFactory(
        configProvider = Provider { appNetworkConfig() },
        authTokenProvider = FirebaseAuthTokenProvider()
    )
}
