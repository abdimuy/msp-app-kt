package com.example.msp_app.core.appgate.download

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.msp_app.core.appgate.UpdatePackage

/** Nombre único del trabajo: nunca dos descargas del mismo APK a la vez. */
internal const val UPDATE_DOWNLOAD_WORK = "msp_update_download"

internal const val KEY_APK_URL = "apk_url"
internal const val KEY_APK_SIZE = "apk_size"
internal const val KEY_APK_SHA256 = "apk_sha256"

/**
 * La política de red, sola y comprobable.
 *
 * - **Automática → solo wifi** ([NetworkType.UNMETERED]). No se le gastan los
 *   datos a nadie sin permiso; es la mitad del trato que hace que actualizar
 *   deje de costar.
 * - **Manual → cualquier red** ([NetworkType.CONNECTED]). Si él lo pide
 *   sabiendo el peso, es su decisión.
 */
fun updateDownloadConstraints(automatic: Boolean): Constraints = Constraints.Builder()
    .setRequiredNetworkType(if (automatic) NetworkType.UNMETERED else NetworkType.CONNECTED)
    .build()

/**
 * Encola la descarga del APK en segundo plano.
 *
 * WorkManager y no una corrutina: la descarga automática tiene que sobrevivir
 * a que el usuario cierre la app y tiene que esperar al wifi aunque eso tarde
 * horas. Ninguna de las dos cosas la da un `CoroutineScope` de proceso.
 *
 * [ExistingWorkPolicy.KEEP] para la automática (si ya está bajando, no se
 * reencola) y [ExistingWorkPolicy.REPLACE] para la manual: un toque explícito
 * debe reemplazar a una automática que está esperando un wifi que no llega.
 */
class UpdateDownloadScheduler(private val workManager: WorkManager) {

    fun enqueueAutomatic(update: UpdatePackage) {
        enqueue(update, automatic = true, policy = ExistingWorkPolicy.KEEP)
    }

    fun enqueueManual(update: UpdatePackage) {
        enqueue(update, automatic = false, policy = ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(update: UpdatePackage, automatic: Boolean, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setConstraints(updateDownloadConstraints(automatic))
            .setInputData(update.toInputData())
            .build()
        workManager.enqueueUniqueWork(UPDATE_DOWNLOAD_WORK, policy, request)
    }
}

internal fun UpdatePackage.toInputData(): Data = Data.Builder()
    .putString(KEY_APK_URL, url)
    .putLong(KEY_APK_SIZE, sizeBytes)
    .putString(KEY_APK_SHA256, sha256)
    .build()

internal fun Data.toUpdatePackage(): UpdatePackage? {
    val url = getString(KEY_APK_URL)
    val sha256 = getString(KEY_APK_SHA256)
    if (url.isNullOrBlank() || sha256.isNullOrBlank()) return null
    return UpdatePackage(url = url, sizeBytes = getLong(KEY_APK_SIZE, 0L), sha256 = sha256)
}
