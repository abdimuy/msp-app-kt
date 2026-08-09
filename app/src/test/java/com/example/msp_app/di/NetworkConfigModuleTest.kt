package com.example.msp_app.di

import com.example.msp_app.BuildConfig
import com.example.msp_app.data.api.FirebaseAuthTokenProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T7 (Plan 4): [NetworkConfigModule] debe exponer la `NetworkConfig` derivada del
 * `BuildConfig` del flavor de gate (`devlocalDebug`) y la impl Firebase del puerto
 * de token — las dos dependencias del `RetrofitClientFactory` de `:core:network`.
 */
class NetworkConfigModuleTest {

    @Test
    fun `provideNetworkConfig trae las URLs del BuildConfig del flavor`() {
        val config = NetworkConfigModule.provideNetworkConfig()

        assertEquals(BuildConfig.LEGACY_BASE_URL, config.legacyBaseUrl)
        assertEquals(BuildConfig.V2_BASE_URL, config.v2BaseUrl)
        assertEquals(BuildConfig.IMAGES_BASE_URL, config.imagesBaseUrl)
    }

    @Test
    fun `provideNetworkConfig deriva appVersion del VERSION_NAME sin el sufijo del flavor`() {
        val config = NetworkConfigModule.provideNetworkConfig()

        // Misma fuente que Constants.APP_VERSION (telemetría/updates): el sufijo
        // `-local+sha` se recorta para que el header quede atribuible al SHA.
        assertEquals(BuildConfig.VERSION_NAME.substringBefore("-"), config.appVersion)
    }

    @Test
    fun `provideAuthTokenProvider es la impl Firebase`() {
        val provider = NetworkConfigModule.provideAuthTokenProvider()

        assertTrue(provider is FirebaseAuthTokenProvider)
    }
}
