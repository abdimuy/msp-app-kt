package com.example.msp_app.core.appgate

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * El veredicto ya resuelto, más lo que la pantalla necesita para explicarlo.
 */
data class VersionGateStatus(
    val verdict: VersionVerdict,
    val installedVersionName: String,
    val requiredVersionName: String,
    val deadlineLabel: String,
    val updatePackage: UpdatePackage?
) {
    val blocked: Boolean get() = verdict == VersionVerdict.BLOCKED
}

/**
 * Une las cuatro piezas: configuración remota, caché en disco, versión
 * instalada y exenciones.
 *
 * **El sentido de las flechas importa**: la red solo escribe en la caché
 * ([syncRemote]) y la UI solo lee de la caché ([status]). Nunca red → UI. Es
 * lo que hace que el bloqueo siga puesto en un sótano sin señal, y también lo
 * que evita el parpadeo "permitido → bloqueado" en cada arranque mientras
 * Firestore responde.
 */
@Singleton
class AppVersionGate @Inject constructor(
    private val cache: VersionGateCache,
    private val remote: MinVersionConfigSource,
    private val buildInfo: AppBuildInfo,
    deviceIdProvider: DeviceIdProvider
) {
    // Se lee una vez: `Settings.Secure.ANDROID_ID` no cambia mientras el
    // proceso vive, y consultarlo en cada emisión del `Flow` sería un viaje al
    // `ContentResolver` por nada.
    private val deviceId: String? by lazy { deviceIdProvider.deviceId() }

    val status: Flow<VersionGateStatus> = cache.config.map { config -> config.toStatus() }

    /**
     * Escucha la configuración remota y la vuelca a la caché. No termina: se
     * lanza en un scope de proceso y vive lo que viva la app.
     */
    suspend fun syncRemote() {
        remote.observe().collect { config -> cache.save(config) }
    }

    private fun MinVersionConfig.toStatus(): VersionGateStatus {
        val exempt = isVersionGateExempt(
            debugBuild = buildInfo.debugBuild,
            deviceId = deviceId,
            exemptDeviceIds = exemptDeviceIds
        )
        return VersionGateStatus(
            verdict = decideVersionGate(
                installedVersionCode = buildInfo.versionCode,
                minVersionCode = minVersionCode,
                exempt = exempt
            ),
            installedVersionName = buildInfo.versionName,
            requiredVersionName = minVersionName,
            deadlineLabel = deadlineLabel,
            updatePackage = updatePackage
        )
    }
}
