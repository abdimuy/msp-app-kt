package com.example.msp_app.core.appgate

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM puro, mismo molde que `DataStoreSettingsRepositoryTest` de
 * `:core:settings`: `PreferenceDataStoreFactory.create` sobre un archivo de
 * [TemporaryFolder], sin Robolectric ni contexto Android.
 *
 * Lo que se prueba es lo que hace que el bloqueo funcione **sin señal**: lo
 * guardado se vuelve a leer igual, y sin nada guardado la compuerta queda
 * apagada (nunca bloqueada por defecto).
 */
class DataStoreVersionGateCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newDataStore(): DataStore<Preferences> {
        val file = File(tempFolder.newFolder(), "test_app_gate.preferences_pb")
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun newCache(): VersionGateCache = DataStoreVersionGateCache(newDataStore())

    private val paquete = UpdatePackage(
        url = "https://example.invalid/msp-app-2.17.0.apk",
        sizeBytes = 11_000_000L,
        sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
    )

    // --- Defaults sin escritura previa ------------------------------------------

    @Test
    fun `sin nada guardado la compuerta queda apagada`() = runTest {
        val config = newCache().config.first()

        assertEquals(NO_MINIMUM_VERSION_CODE, config.minVersionCode)
        assertEquals("", config.minVersionName)
        assertEquals("", config.deadlineLabel)
        assertEquals(emptySet<String>(), config.exemptDeviceIds)
        assertNull(config.updatePackage)
    }

    // --- Round-trip --------------------------------------------------------------

    @Test
    fun `guarda y relee la configuracion completa`() = runTest {
        val cache = newCache()
        val original = MinVersionConfig(
            minVersionCode = 58,
            minVersionName = "2.17.0",
            exemptDeviceIds = setOf("a1b2c3d4e5f60718"),
            deadlineLabel = "vie 22",
            updatePackage = paquete
        )

        cache.save(original)

        assertEquals(original, cache.config.first())
    }

    @Test
    fun `una segunda escritura reemplaza a la primera`() = runTest {
        val cache = newCache()
        cache.save(MinVersionConfig(minVersionCode = 57, minVersionName = "2.16.5"))

        cache.save(MinVersionConfig(minVersionCode = 58, minVersionName = "2.17.0"))

        val config = cache.config.first()
        assertEquals(58, config.minVersionCode)
        assertEquals("2.17.0", config.minVersionName)
    }

    @Test
    fun `retirar el APK de la configuracion lo borra de la cache`() = runTest {
        val cache = newCache()
        cache.save(MinVersionConfig(minVersionCode = 58, updatePackage = paquete))

        cache.save(MinVersionConfig(minVersionCode = 58, updatePackage = null))

        // Si quedaran restos, la app seguiría intentando bajar un APK retirado.
        assertNull(cache.config.first().updatePackage)
    }

    @Test
    fun `un APK sin checksum no se considera un APK`() = runTest {
        val cache = newCache()

        cache.save(MinVersionConfig(updatePackage = paquete.copy(sha256 = "")))

        assertNull(cache.config.first().updatePackage)
    }

    /**
     * Sin tamaño, `UpdateFileLocator.isComplete` no puede afirmar nunca que el
     * archivo esté entero, y la pantalla se quedaba en «0 de 0 MB · 0%» para
     * siempre. Media configuración no es una actualización: mejor decir que no
     * hay APK publicado.
     */
    @Test
    fun `un APK sin tamaño no se considera un APK`() = runTest {
        val cache = newCache()

        cache.save(MinVersionConfig(updatePackage = paquete.copy(sizeBytes = 0L)))

        assertNull(cache.config.first().updatePackage)
    }

    @Test
    fun `un APK sin URL no se considera un APK`() = runTest {
        val cache = newCache()

        cache.save(MinVersionConfig(updatePackage = paquete.copy(url = "")))

        assertNull(cache.config.first().updatePackage)
    }
}
