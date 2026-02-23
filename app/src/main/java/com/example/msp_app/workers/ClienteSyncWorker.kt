package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.data.repository.ClienteRepository

class ClienteSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository = ClienteRepository(appContext)
    private val logger: RemoteLogger by lazy { RemoteLogger.getInstance(appContext) }

    override suspend fun doWork(): Result {
        return try {
            repository.syncFromServer()

            val count = repository.getCount()
            Log.d("ClienteSyncWorker", "Sincronización completada: $count clientes")

            val prefs = applicationContext.getSharedPreferences("cliente_sync", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply()

            logger.info(
                module = "CLIENTE_SYNC",
                action = "SYNC_SUCCESS",
                message = "Clientes sincronizados exitosamente",
                data = mapOf("clienteCount" to count)
            )

            Result.success()
        } catch (e: Exception) {
            Log.e("ClienteSyncWorker", "Error al sincronizar clientes", e)

            logger.error(
                module = "CLIENTE_SYNC",
                action = "SYNC_ERROR",
                message = "Error al sincronizar clientes: ${e.message}",
                error = e,
                data = mapOf("attemptCount" to runAttemptCount)
            )

            Result.retry()
        }
    }
}
