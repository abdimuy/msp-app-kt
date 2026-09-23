package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.sync.pendingwork.data.visits.VisitsReconcileTriggerProvider

/**
 * Runs the visitas reconciler's app-open and periodic triggers outside the UI
 * lifecycle — same pattern as [CobranzaReconcileWorker]: plain
 * `(Context, WorkerParameters)` constructor (`HiltWorkerFactory` falls back to
 * it, see `HiltWorkerFactoryFallbackTest`), the real dependency resolved
 * **inside** `doWork()` via a process-wide provider rather than frozen at
 * construction — [VisitsReconcileTriggerProvider.get] re-resolves
 * `ReconcileVisitsUseCase` from the Hilt graph on every actual run, keeping
 * the baseURL kill-switch reachable.
 *
 * [enqueueVisitsReconcileNowWorker] and [enqueueVisitsReconcilePeriodicWorker]
 * (`WorkManagerUtils.kt`) both target THIS worker class and both route through
 * [VisitsReconcileTriggerProvider]'s single process-wide
 * `TriggerVisitsReconciliationUseCase`, so a periodic tick landing while the
 * app-open run is still in flight (or vice versa) is a guard skip, not a
 * second reconciliation — see that class's KDoc for the mechanism and
 * `TriggerVisitsReconciliationUseCaseTest` for the proof.
 *
 * Always [Result.success], never [Result.retry]: [TriggerVisitsReconciliationUseCase
 * .execute] never throws (every internal failure is caught and reported via
 * `SyncErrorReporter`, idempotently — a failed chunk just leaves its ids
 * pending for the next trigger to re-ask about). Asking WorkManager to also
 * retry on top would only race that next trigger for no benefit.
 */
class VisitsReconcileWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val trigger: suspend () -> Unit = {
        VisitsReconcileTriggerProvider.get(appContext).execute()
    }
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            trigger()
            Result.success()
        } catch (e: Exception) {
            // Defensa: el guard y `ReconcileVisitsUseCase` ya reportan y
            // absorben todo lo esperable (ver KDoc de clase); esto solo cubre
            // un escape genuinamente inesperado. `:app` es legacy y queda
            // fuera de la norma de errores (Ruling I) — Log.w es lo mismo que
            // usa `CobranzaReconcileWorker` al lado.
            Log.w(TAG, "trigger de reconciliacion de visitas fallo de forma inesperada", e)
            Result.success()
        }
    }

    companion object {
        private const val TAG = "VisitsReconcileWorker"
    }
}
