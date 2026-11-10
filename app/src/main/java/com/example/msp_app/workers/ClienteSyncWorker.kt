package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.common.busqueda.TopeDeDescarga
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.data.repository.ClienteRepository
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException

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

        return ejecutarSyncDeClientes(
            repository = repository,
            intento = runAttemptCount,
            alExito = { count ->
                logger.info(
                    module = "CLIENTE_SYNC",
                    action = "SYNC_SUCCESS",
                    message = "Clientes sincronizados exitosamente",
                    data = mapOf("clienteCount" to count)
                )
            },
            alError = { e ->
                logger.error(
                    module = "CLIENTE_SYNC",
                    action = "SYNC_ERROR",
                    message = "Error al sincronizar clientes: ${e.message}",
                    error = e,
                    data = mapOf("attemptCount" to runAttemptCount)
                )
            }
        )
    }
}

/**
 * Cuerpo del worker, separado de WorkManager para probarlo con reloj virtual (E-APP-158).
 *
 * - Tope TOTAL propio ([TopeDeDescarga.SEGUNDO_PLANO_MS]): el read timeout de 300 s del cliente
 *   legado mide inactividad, no duración. Si vence → `retry()` (backoff normal de WorkManager).
 * - Una lista vacía del servidor no reemplaza el padrón (el repositorio la ignora y la registra);
 *   el trabajo termina en `success`: reintentar no cambiaría lo que contesta el servidor.
 * - La cancelación (WorkManager detiene el trabajo) se PROPAGA: no es un error de red.
 */
internal suspend fun ejecutarSyncDeClientes(
    repository: ClienteRepository,
    intento: Int,
    alExito: (Int) -> Unit,
    alError: (Exception) -> Unit,
    topeMs: Long = TopeDeDescarga.SEGUNDO_PLANO_MS
): ListenableWorker.Result = try {
    val reemplazado = repository.syncFromServer(topeMs)
    val count = repository.getCount()
    Log.d(
        "ClienteSyncWorker",
        "Sincronización completada: $count clientes (reemplazado=$reemplazado, intento=$intento)"
    )
    if (reemplazado) alExito(count)
    ListenableWorker.Result.success()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.e("ClienteSyncWorker", "Error al sincronizar clientes", e)
    alError(e)
    ListenableWorker.Result.retry()
}
