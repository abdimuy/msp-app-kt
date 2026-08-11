package com.example.msp_app.core.settings.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.msp_app.core.settings.DataStoreSettingsRepository
import com.example.msp_app.core.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prueba que el grafo Hilt REAL resuelve [SettingsRepository] →
 * [DataStoreSettingsRepository] y el `DataStore<Preferences>` subyacente a
 * través de [SettingsModule], y que ambos bindings son `@Singleton` (misma
 * instancia entre dos puntos de inyección) — mismo patrón que
 * `TelemetryModuleHiltGraphTest`/`TelemetryDatabaseModuleHiltGraphTest`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class SettingsModuleHiltGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settingsRepository: SettingsRepository

    // Segundo punto de inyección del MISMO tipo, para probar el scope
    // `@Singleton` sin un `EntryPoint` propio: si el binding es singleton,
    // ambos campos deben resolver a la misma instancia.
    @Inject
    lateinit var settingsRepositoryAgain: SettingsRepository

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Test
    fun `el grafo Hilt resuelve SettingsRepository como DataStoreSettingsRepository`() {
        hiltRule.inject()

        assertTrue(settingsRepository is DataStoreSettingsRepository)
    }

    @Test
    fun `el grafo Hilt resuelve el DataStore de preferencias`() {
        hiltRule.inject()

        assertNotNull(dataStore)
    }

    @Test
    fun `SettingsRepository es singleton, dos campos inyectados son la misma instancia`() {
        hiltRule.inject()

        assertSame(settingsRepository, settingsRepositoryAgain)
    }
}
