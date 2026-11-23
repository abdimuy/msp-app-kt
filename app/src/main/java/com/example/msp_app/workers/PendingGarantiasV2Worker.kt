package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.feature.garantias.domain.port.ProcesadorDeGarantias
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

/**
 * Una pasada de la cola de captura de garantías v2.
 *
 * No usa `@HiltWorker`: el procesador se resuelve con un [EntryPoint] en cada
 * corrida, igual que los demás trabajadores de sincronización del repo. Así una
 * instancia vieja del trabajador no se queda con un grafo congelado, y el
 * `HiltWorkerFactory` no necesita conocer este tipo.
 *
 * Mientras la cola diga que queda trabajo que un reintento puede resolver, se
 * devuelve [Result.retry]; cuando no queda nada, [Result.success]. El comando o
 * la foto rechazados no cuentan como trabajo pendiente: ya tienen su estado
 * visible en la pantalla y reintentarlos sería repetir un error definitivo.
 */
class PendingGarantiasV2Worker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @Suppress("TooGenericExceptionCaught") // Room o red: cualquiera se reintenta
    override suspend fun doWork(): Result = try {
        val resultado = procesador().procesar()
        if (resultado.quedaTrabajo) Result.retry() else Result.success()
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Exception) {
        if (runAttemptCount >= MAX_INTENTOS) {
            Log.e(TAG, "Tope de intentos; la cola queda para la próxima apertura", fallo)
            Result.failure()
        } else {
            Log.w(TAG, "Fallo al procesar la cola de garantías; reintentando", fallo)
            Result.retry()
        }
    }

    private fun procesador(): ProcesadorDeGarantias = EntryPointAccessors.fromApplication(
        applicationContext,
        GarantiasV2EntryPoint::class.java
    ).procesador()

    private companion object {
        const val TAG = "GarantiasV2"
        const val MAX_INTENTOS = 10
    }
}

/**
 * La puerta al grafo de Hilt desde el trabajador.
 *
 * Está en el archivo del trabajador a propósito: es su única razón de existir y
 * así no se puede borrar una mitad sin que la otra deje de compilar.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GarantiasV2EntryPoint {
    fun procesador(): ProcesadorDeGarantias
}
