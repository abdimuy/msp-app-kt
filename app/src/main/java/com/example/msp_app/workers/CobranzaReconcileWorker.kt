package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.sync.cobranza.CobranzaReconcilerProvider
import com.example.msp_app.core.sync.cobranza.ReconcileOutcome

/**
 * Corre `CobranzaReconciler.reconcileNow()` fuera del ciclo de vida de la UI.
 *
 * Antes el reconciliador vivía en un bucle dentro de [
 * com.example.msp_app.core.sync.cobranza.CobranzaSyncObserver]: el `delay` iba
 * **antes** de la primera vuelta y el job moría en `ON_STOP`. Eso exigía cinco
 * minutos ininterrumpidos de app en primer plano, y el uso real de un cobrador
 * son ráfagas de segundos — medido: **no corrió nunca en ningún teléfono de la
 * flota**.
 *
 * Importa porque `reconcileNow()` es la única ruta que detecta pagos faltantes
 * y los pide por el canal `by-ids`, que **no tiene watermark**: es la defensa
 * robusta contra un servidor con el sync congelado.
 *
 * Sigue el mismo patrón que [PendingPaymentsWorker]: constructor plano
 * `(Context, WorkerParameters)` con las dependencias como argumentos por
 * defecto (`HiltWorkerFactory` devuelve null y WorkManager cae al constructor
 * reflectivo), red requerida como constraint y trabajo único por nombre.
 *
 * El reconciliador se resuelve **dentro** de `doWork()`, no al construir el
 * worker: [CobranzaReconcilerProvider] arma el `V2CobranzaApi` con el `baseURL`
 * vigente, y congelarlo en la construcción rompería el kill-switch de la flota.
 */
class CobranzaReconcileWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val reconcile: suspend () -> ReconcileOutcome = {
        CobranzaReconcilerProvider.get(appContext).reconcileNow()
    }
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            when (val outcome = reconcile()) {
                is ReconcileOutcome.Ok -> {
                    Log.i(
                        TAG,
                        "reconcile ok: pagosFantasma=${outcome.pagosPhantomsDeleted} " +
                            "saldosFantasma=${outcome.saldosPhantomsDeleted} " +
                            "pagosExtra=${outcome.pagosExtrasOnServer} " +
                            "saldosExtra=${outcome.saldosExtrasOnServer}"
                    )
                    Result.success()
                }

                // Sin sesión no hay zona que reconciliar. No es un fallo: la
                // próxima apertura de la app vuelve a encolar.
                ReconcileOutcome.SkippedNoZone -> Result.success()

                // La constraint de red debería evitarlo, pero si se cuela hay
                // que volver: soltar la corrida deja pagos sin rescatar.
                ReconcileOutcome.SkippedOffline -> Result.retry()

                is ReconcileOutcome.Error -> {
                    Log.w(TAG, "reconcile falló, se reintenta", outcome.cause)
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "reconcile lanzó una excepción inesperada, se reintenta", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CobranzaReconcileWorker"
    }
}
