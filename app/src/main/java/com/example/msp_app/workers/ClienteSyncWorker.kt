package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.data.repository.ClienteRepository
import java.time.LocalDateTime

/** Inicio de la jornada laboral del negocio (zona de negocio), inclusive. */
private const val WORKING_HOURS_START = 7

/** Fin de la jornada laboral del negocio (zona de negocio), exclusive. */
private const val WORKING_HOURS_END = 18

/**
 * Predicado puro: ¿[now] cae dentro del horario laboral del negocio?
 *
 * [now] debe obtenerse vía [AppTime.nowInBusinessZone] — nunca de la zona del dispositivo,
 * para que el gate sea el mismo sin importar dónde esté físicamente el cobrador.
 */
fun isWithinWorkingHours(now: LocalDateTime): Boolean =
    now.hour in WORKING_HOURS_START until WORKING_HOURS_END

class ClienteSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository = ClienteRepository(appContext)
    private val logger: RemoteLogger by lazy { RemoteLogger.getInstance(appContext) }

    override suspend fun doWork(): Result {
        val now = AppTime.nowInBusinessZone(AppClock.System)
        if (!isWithinWorkingHours(now)) {
            Log.d("ClienteSyncWorker", "Fuera de horario laboral (${now.hour} hrs), omitiendo sync")
            return Result.success()
        }

        return try {
            repository.syncFromServer()

            val count = repository.getCount()
            Log.d("ClienteSyncWorker", "Sincronización completada: $count clientes")

            val prefs = applicationContext.getSharedPreferences(
                "cliente_sync",
                Context.MODE_PRIVATE
            )
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
