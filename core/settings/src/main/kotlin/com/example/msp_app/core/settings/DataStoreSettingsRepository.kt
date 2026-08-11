package com.example.msp_app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Claves DataStore de [DataStoreSettingsRepository] — `internal` porque
 * ningún otro archivo del módulo (ni de fuera) necesita leerlas directo, solo
 * a través del [SettingsRepository].
 */
internal object SettingsKeys {
    val FONT_SIZE_LEVEL = stringPreferencesKey("font_size_level")
    val PRIVACY_MASKED = booleanPreferencesKey("privacy_masked")
    val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
}

/**
 * Implementación de [SettingsRepository] sobre **DataStore Preferences**
 * (mismo stack que `SaleDraftManager`, ya en la app — ver spec
 * §"`:core:settings`"). Un solo DataStore app-scoped, inyectado por Hilt
 * ([com.example.msp_app.core.settings.di.SettingsModule]).
 *
 * Robustez ante datos corruptos (spec: "robusto a valores mal guardados"):
 * - [Flow.catch] atrapa una [IOException] de lectura del archivo (disco lleno,
 *   corrupción) y degrada a [emptyPreferences] — de ahí todas las claves
 *   faltan y cada `map` cae a su default, exactamente igual que un primer
 *   arranque sin ajustes guardados.
 * - [fontSizeLevel] guarda el nivel como el `name` del enum (`stringPreferencesKey`,
 *   no un índice ordinal — más legible en el archivo y estable si el enum
 *   se reordena). Un string que no matchea ningún [FontSizeLevel] (versión
 *   vieja de la app, corrupción parcial) cae a [FontSizeLevel.NORMAL] en vez
 *   de lanzar `IllegalArgumentException`.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) {
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

    override val fontSizeLevel: Flow<FontSizeLevel> = safeData.map { prefs ->
        prefs[SettingsKeys.FONT_SIZE_LEVEL]?.let { raw ->
            runCatching { FontSizeLevel.valueOf(raw) }.getOrDefault(FontSizeLevel.NORMAL)
        } ?: FontSizeLevel.NORMAL
    }

    override suspend fun setFontSizeLevel(level: FontSizeLevel) {
        dataStore.edit { prefs -> prefs[SettingsKeys.FONT_SIZE_LEVEL] = level.name }
    }

    override val privacyMasked: Flow<Boolean> = safeData.map { prefs ->
        prefs[SettingsKeys.PRIVACY_MASKED] ?: false
    }

    override suspend fun setPrivacyMasked(masked: Boolean) {
        dataStore.edit { prefs -> prefs[SettingsKeys.PRIVACY_MASKED] = masked }
    }

    override val reduceMotion: Flow<Boolean> = safeData.map { prefs ->
        prefs[SettingsKeys.REDUCE_MOTION] ?: false
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SettingsKeys.REDUCE_MOTION] = enabled }
    }
}
