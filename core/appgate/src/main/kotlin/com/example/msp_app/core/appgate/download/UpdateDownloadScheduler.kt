package com.example.msp_app.core.appgate.download

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.msp_app.core.appgate.UpdatePackage
import kotlinx.coroutines.flow.first

/** Nombre único del trabajo: nunca dos descargas del mismo APK a la vez. */
internal const val UPDATE_DOWNLOAD_WORK = "msp_update_download"

/**
 * Prefijo de la etiqueta que dice **qué** paquete trae el trabajo encolado.
 *
 * WorkManager no expone los datos de entrada de un trabajo pendiente, pero sí
 * sus etiquetas. Es el único canal que sobrevive a que el proceso muera: un
 * campo en memoria daría la respuesta equivocada justo después de un reinicio,
 * que es cuando el trabajo viejo sigue en backoff y hay que decidir.
 */
internal const val TAG_APK_SHA256_PREFIX = "apk_sha256:"

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
 * La manual siempre usa [ExistingWorkPolicy.REPLACE]: un toque explícito debe
 * reemplazar a una automática que está esperando un wifi que no llega. La
 * automática decide en [automaticPolicyFor].
 */
class UpdateDownloadScheduler(private val workManager: WorkManager) {

    suspend fun enqueueAutomatic(update: UpdatePackage) {
        enqueue(update, automatic = true, policy = automaticPolicyFor(update))
    }

    fun enqueueManual(update: UpdatePackage) {
        enqueue(update, automatic = false, policy = ExistingWorkPolicy.REPLACE)
    }

    /**
     * [ExistingWorkPolicy.KEEP] si lo que ya está en cola es el **mismo**
     * paquete; [ExistingWorkPolicy.REPLACE] si es otro.
     *
     * Por qué no siempre `REPLACE`: la configuración remota emite en cada
     * cambio del documento y en cada reconexión, y `REPLACE` cancela el
     * trabajo en curso y lo vuelve a encolar de cero — con emisiones seguidas
     * la descarga **nunca termina**. `KEEP` existe exactamente para eso.
     *
     * Por qué no siempre `KEEP`: si la oficina republica el paquete corregido
     * (otro SHA/URL/tamaño) mientras el trabajo viejo está en backoff con los
     * datos anteriores, `KEEP` ignora la corrección y el teléfono se queda
     * pidiendo un archivo que ya no existe hasta que ese trabajo muera solo.
     *
     * Por qué no un nombre único derivado del SHA: dos trabajos vivos a la vez
     * se pelean por `filesDir/updates` y el `clearOthers` de uno borra el
     * parcial del otro.
     *
     * Un trabajo ya terminado no cuenta como pendiente: no hay nada que
     * conservar ni que cancelar, y WorkManager lo reemplaza igual con `KEEP`.
     */
    internal suspend fun automaticPolicyFor(update: UpdatePackage): ExistingWorkPolicy {
        val pending = pendingWork() ?: return ExistingWorkPolicy.KEEP
        return if (update.workTag() in pending.tags) {
            ExistingWorkPolicy.KEEP
        } else {
            ExistingWorkPolicy.REPLACE
        }
    }

    /**
     * `getWorkInfosForUniqueWorkFlow` y no el `ListenableFuture` hermano: la
     * variante `Flow` ya viene en `work-runtime` y se espera con `first()`,
     * sin arrastrar `kotlinx-coroutines-guava` solo para un `await`.
     */
    private suspend fun pendingWork(): WorkInfo? =
        workManager.getWorkInfosForUniqueWorkFlow(UPDATE_DOWNLOAD_WORK)
            .first()
            .firstOrNull { !it.state.isFinished }

    private fun enqueue(update: UpdatePackage, automatic: Boolean, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setConstraints(updateDownloadConstraints(automatic))
            .setInputData(update.toInputData())
            .addTag(update.workTag())
            .build()
        workManager.enqueueUniqueWork(UPDATE_DOWNLOAD_WORK, policy, request)
    }
}

/** Etiqueta que identifica al paquete dentro del trabajo único. */
internal fun UpdatePackage.workTag(): String = TAG_APK_SHA256_PREFIX + sha256

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
