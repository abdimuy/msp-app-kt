package com.example.msp_app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM puro: `PreferenceDataStoreFactory.create` con `produceFile` apuntando a
 * un archivo de [TemporaryFolder] (creado/borrado por test, nunca comparte
 * estado con el DataStore real `msp_settings` que Hilt provee en prod) — no
 * necesita Robolectric ni contexto Android, mismo criterio que
 * `RoomTestBase`/`RobolectricTestBase` reservan para lo que SÍ lo requiere.
 *
 * Cubre lo que el brief pide: round-trip de cada ajuste, defaults correctos
 * sin escritura previa, y un valor de enum desconocido cae a `NORMAL` en vez
 * de reventar.
 */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newRepository(): SettingsRepository = DataStoreSettingsRepository(newDataStore())

    private fun newDataStore(): DataStore<Preferences> {
        val file = File(tempFolder.newFolder(), "test_settings.preferences_pb")
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    // --- Defaults sin escritura previa --------------------------------------

    @Test
    fun `fontSizeLevel default es NORMAL`() = runTest {
        assertEquals(FontSizeLevel.NORMAL, newRepository().fontSizeLevel.first())
    }

    @Test
    fun `privacyMasked default es false`() = runTest {
        assertFalse(newRepository().privacyMasked.first())
    }

    @Test
    fun `reduceMotion default es false`() = runTest {
        assertFalse(newRepository().reduceMotion.first())
    }

    // --- Round-trip ----------------------------------------------------------

    @Test
    fun `fontSizeLevel hace round-trip para cada nivel`() = runTest {
        val dataStore = newDataStore()
        val repository = DataStoreSettingsRepository(dataStore)

        for (level in FontSizeLevel.entries) {
            repository.setFontSizeLevel(level)
            assertEquals(level, repository.fontSizeLevel.first())
        }
    }

    @Test
    fun `privacyMasked hace round-trip`() = runTest {
        val repository = newRepository()

        repository.setPrivacyMasked(true)
        assertEquals(true, repository.privacyMasked.first())

        repository.setPrivacyMasked(false)
        assertEquals(false, repository.privacyMasked.first())
    }

    @Test
    fun `reduceMotion hace round-trip`() = runTest {
        val repository = newRepository()

        repository.setReduceMotion(true)
        assertEquals(true, repository.reduceMotion.first())

        repository.setReduceMotion(false)
        assertEquals(false, repository.reduceMotion.first())
    }

    // --- Robustez ante datos corruptos ----------------------------------------

    @Test
    fun `un string de nivel desconocido cae a NORMAL en vez de reventar`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("font_size_level")] = "GIGANTESCO_INEXISTENTE"
        }
        val repository = DataStoreSettingsRepository(dataStore)

        assertEquals(FontSizeLevel.NORMAL, repository.fontSizeLevel.first())
    }
}
