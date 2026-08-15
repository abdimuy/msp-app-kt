package com.example.msp_app.core.appgate.di

import com.example.msp_app.core.appgate.AppBuildInfo
import com.example.msp_app.core.appgate.AppVersionGate
import com.example.msp_app.core.appgate.DataStoreVersionGateCache
import com.example.msp_app.core.appgate.DeviceIdProvider
import com.example.msp_app.core.appgate.FirestoreMinVersionConfigSource
import com.example.msp_app.core.appgate.MinVersionConfigSource
import com.example.msp_app.core.appgate.VersionGateCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `AppBuildInfo` es el ÚNICO binding que [AppGateModule] deja abierto a
 * propósito: solo `:app` conoce su propio `BuildConfig`. Acá lo suple el
 * source set de test, que es exactamente el papel que en producción juega el
 * módulo de la raíz de composición.
 */
@Module
@InstallIn(SingletonComponent::class)
object TestAppBuildInfoModule {

    @Provides
    @Singleton
    fun provideAppBuildInfo(): AppBuildInfo =
        AppBuildInfo(versionCode = 56, versionName = "2.16.0", debugBuild = false)
}

/**
 * Prueba que el grafo Hilt REAL resuelve la compuerta — mismo patrón que
 * `SettingsModuleHiltGraphTest` de `:core:settings`.
 *
 * NO se inyecta `UpdateDownloadScheduler`: su `@Provides` llama a
 * `WorkManager.getInstance`, que en un `HiltTestApplication` de Robolectric no
 * está inicializado. Los proveedores de Dagger son perezosos, así que no
 * pedirlo mantiene el resto del grafo comprobable sin montar WorkManager solo
 * para esto.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class AppGateModuleHiltGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var cache: VersionGateCache

    @Inject
    lateinit var cacheAgain: VersionGateCache

    @Inject
    lateinit var configSource: MinVersionConfigSource

    @Inject
    lateinit var deviceIdProvider: DeviceIdProvider

    @Inject
    lateinit var gate: AppVersionGate

    @Test
    fun `el grafo resuelve la cache como DataStoreVersionGateCache`() {
        hiltRule.inject()

        assertTrue(cache is DataStoreVersionGateCache)
    }

    @Test
    fun `la cache es singleton, dos campos inyectados son la misma instancia`() {
        hiltRule.inject()

        assertSame(cache, cacheAgain)
    }

    @Test
    fun `el grafo resuelve la fuente remota sin inicializar Firebase`() {
        hiltRule.inject()

        // Construir el binding NO debe tocar `FirebaseFirestore.getInstance()`:
        // eso solo ocurre cuando alguien llama `observe()`.
        assertTrue(configSource is FirestoreMinVersionConfigSource)
    }

    @Test
    fun `el grafo resuelve el proveedor de deviceId y la compuerta completa`() {
        hiltRule.inject()

        assertNotNull(deviceIdProvider)
        assertNotNull(gate)
    }
}
