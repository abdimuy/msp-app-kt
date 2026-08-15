package com.example.msp_app.core.appgate.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.appgate.UpdatePackage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Baja el APK en segundo plano. La restricción de red (wifi para la
 * automática, cualquiera para la manual) la puso [UpdateDownloadScheduler] al
 * encolar — este worker no vuelve a decidirla.
 *
 * Reintento vs. rendición:
 * - descarga cortada → `retry`, y lo bajado se conserva (WorkManager reintenta
 *   con backoff cuando la restricción de red se vuelva a cumplir);
 * - checksum que no coincide → `retry` también, pero con el archivo ya
 *   borrado: se vuelve a bajar de cero, que es lo único que puede arreglarlo;
 * - sin datos de entrada válidos → `failure`, no hay nada que reintentar.
 */
@HiltWorker
class ApkDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloader: ApkDownloader,
    private val locator: UpdateFileLocator,
    private val stateHolder: UpdateDownloadStateHolder
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val update = inputData.toUpdatePackage() ?: return Result.failure()
        locator.clearOthers(update)
        return runDownload(update)
    }

    private suspend fun runDownload(update: UpdatePackage): Result {
        val destination = locator.fileFor(update)
        val outcome = downloader.download(update, destination) { progress ->
            stateHolder.update(UpdateDownloadState.Running(progress))
        }
        return when (outcome) {
            is DownloadOutcome.Completed -> {
                stateHolder.update(UpdateDownloadState.Ready(outcome.file))
                Result.success()
            }

            is DownloadOutcome.Failed -> {
                stateHolder.update(
                    UpdateDownloadState.Paused(
                        DownloadProgress(locator.downloadedBytes(update), update.sizeBytes)
                    )
                )
                Result.retry()
            }

            DownloadOutcome.IntegrityFailed -> {
                // El parcial ya lo borró el downloader: se reintenta desde cero.
                stateHolder.update(
                    UpdateDownloadState.Paused(DownloadProgress(0L, update.sizeBytes))
                )
                Result.retry()
            }
        }
    }
}
