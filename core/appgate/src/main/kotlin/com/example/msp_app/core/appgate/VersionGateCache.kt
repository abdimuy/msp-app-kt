package com.example.msp_app.core.appgate

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Claves DataStore de [DataStoreVersionGateCache] — `internal` por la misma
 * razón que `SettingsKeys`: nadie las lee fuera de esta clase.
 */
internal object VersionGateKeys {
    val MIN_VERSION_CODE = intPreferencesKey("min_version_code")
    val MIN_VERSION_NAME = stringPreferencesKey("min_version_name")
    val DEADLINE_LABEL = stringPreferencesKey("min_version_deadline")
    val EXEMPT_DEVICE_IDS = stringSetPreferencesKey("min_version_exempt_devices")
    val APK_URL = stringPreferencesKey("min_version_apk_url")
    val APK_SIZE = longPreferencesKey("min_version_apk_size")
    val APK_SHA256 = stringPreferencesKey("min_version_apk_sha256")
}

/**
 * El último veredicto conocido, en disco.
 *
 * **Es la pieza que hace que el bloqueo funcione sin señal.** El cobrador
 * pasa la jornada sin datos; si la compuerta dependiera de poder leer
 * Firestore en el arranque, bastaría con abrir la app en un sótano para
 * saltársela. Por eso la UI **siempre** lee de aquí y la red solo escribe:
 * remoto → caché → decisión, nunca remoto → decisión.
 *
 * Mismo principio que el bloqueo por dispositivo, que ya guarda su
 * autorización local en `SharedPreferences`.
 */
interface VersionGateCache {
    /** Nunca falla y nunca se queda vacío: sin nada guardado emite los defaults. */
    val config: Flow<MinVersionConfig>

    suspend fun save(config: MinVersionConfig)
}

/**
 * Implementación sobre **DataStore Preferences**, molde idéntico al de
 * `DataStoreSettingsRepository` de `:core:settings` (incluido el
 * `catch`/`emptyPreferences` ante [IOException]).
 *
 * Robustez ante datos corruptos: si el archivo no se puede leer (disco lleno,
 * corrupción), se degrada a [emptyPreferences] y de ahí cada campo cae a su
 * default — es decir, **compuerta apagada**. Un archivo ilegible no puede
 * bloquear la app: el modo de falla seguro acá es dejar pasar.
 */
class DataStoreVersionGateCache(
    private val dataStore: DataStore<Preferences>
) : VersionGateCache {

    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) {
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

    override val config: Flow<MinVersionConfig> = safeData.map { prefs ->
        MinVersionConfig(
            minVersionCode = prefs[VersionGateKeys.MIN_VERSION_CODE] ?: NO_MINIMUM_VERSION_CODE,
            minVersionName = prefs[VersionGateKeys.MIN_VERSION_NAME].orEmpty(),
            exemptDeviceIds = prefs[VersionGateKeys.EXEMPT_DEVICE_IDS].orEmpty(),
            deadlineLabel = prefs[VersionGateKeys.DEADLINE_LABEL].orEmpty(),
            updatePackage = prefs.readUpdatePackage()
        )
    }

    override suspend fun save(config: MinVersionConfig) {
        dataStore.edit { prefs ->
            prefs[VersionGateKeys.MIN_VERSION_CODE] = config.minVersionCode
            prefs[VersionGateKeys.MIN_VERSION_NAME] = config.minVersionName
            prefs[VersionGateKeys.DEADLINE_LABEL] = config.deadlineLabel
            prefs[VersionGateKeys.EXEMPT_DEVICE_IDS] = config.exemptDeviceIds
            val pkg = config.updatePackage
            if (pkg == null) {
                // Se BORRAN, no se dejan a medias: un APK retirado de la
                // configuración remota no debe seguir descargándose.
                prefs.remove(VersionGateKeys.APK_URL)
                prefs.remove(VersionGateKeys.APK_SIZE)
                prefs.remove(VersionGateKeys.APK_SHA256)
            } else {
                prefs[VersionGateKeys.APK_URL] = pkg.url
                prefs[VersionGateKeys.APK_SIZE] = pkg.sizeBytes
                prefs[VersionGateKeys.APK_SHA256] = pkg.sha256
            }
        }
    }
}

private fun Preferences.readUpdatePackage(): UpdatePackage? {
    val url = this[VersionGateKeys.APK_URL]
    val sha256 = this[VersionGateKeys.APK_SHA256]
    if (url.isNullOrBlank() || sha256.isNullOrBlank()) return null
    return UpdatePackage(
        url = url,
        sizeBytes = this[VersionGateKeys.APK_SIZE] ?: 0L,
        sha256 = sha256
    )
}
