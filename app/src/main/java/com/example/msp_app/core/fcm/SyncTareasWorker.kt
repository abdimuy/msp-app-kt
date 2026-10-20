package com.example.msp_app.core.fcm

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.msp_app.core.avisos.CanalesDeNotificacion
import com.example.msp_app.core.avisos.ReprogramarAvisosWorker
import com.example.msp_app.core.sync.tareas.TareasZonaSyncProvider
import kotlin.coroutines.cancellation.CancellationException

/** La solicitud del sync que despierta el push: expedited, nunca `DROP_WORK_REQUEST`. */
fun solicitudDeSyncTareas(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncTareasWorker>()
    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .build()

/** Trabajo único `sync_tareas_zona`; `KEEP`: dos pushes seguidos son un solo sync. */
fun encolarSyncTareas(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        SyncTareasWorker.NOMBRE_UNICO,
        ExistingWorkPolicy.KEEP,
        solicitudDeSyncTareas()
    )
}

/**
 * Baja las tareas de la zona (con el aviso inmediato dentro del manager, T12) y
 * reprograma las alarmas (T13). Siempre `success`: el periódico de 15 min es el
 * respaldo (plan P3, Tarea 14; spec §6.4).
 */
class SyncTareasWorker @JvmOverloads constructor(
    appContext: Context,
    params: WorkerParameters,
    @VisibleForTesting
    internal val sincronizar: suspend () -> Unit = {
        TareasZonaSyncProvider.sincronizar(appContext)
        Unit
    },
    @VisibleForTesting
    internal val reprogramar: suspend () -> Unit = {
        ReprogramarAvisosWorker.reprogramarPorOmision(appContext)
    }
) : CoroutineWorker(appContext, params) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        try {
            sincronizar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "el sync de tareas por push fallo; el periodico lo reintenta", e)
        }
        try {
            reprogramar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "la reprogramacion de alarmas fallo", e)
        }
        return Result.success()
    }

    /** Obligatorio para expedited en API < 31. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, CanalesDeNotificacion.SINCRONIZACION)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Sincronizando")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICACION_ID, n)
    }

    companion object {
        const val NOMBRE_UNICO = "sync_tareas_zona"
        private const val NOTIFICACION_ID = 4014
        private const val TAG = "SyncTareasWorker"
    }
}
