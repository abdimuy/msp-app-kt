package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.diagnosticoenvio.EstadoDeEnvioDeVisitas
import com.example.msp_app.core.sync.pendingwork.data.visits.VisitsReconcileTriggerProvider
import com.example.msp_app.workmanager.enqueuePendingVisitsWorker
import kotlinx.coroutines.CancellationException

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
    internal val pendientes: suspend () -> List<String> = { pendientesPorOmision(appContext) },
    @VisibleForTesting
    internal val encolar: (String) -> Unit = { enqueuePendingVisitsWorker(appContext, it) },
    @VisibleForTesting
    internal val trigger: suspend () -> Unit = {
        VisitsReconcileTriggerProvider.get(appContext).execute()
    },
    /**
     * Telemetría `visitas_estado` (plan visitas P1, Tarea 14; H14): solo
     * conteos y antigüedades, lista blanca de `TelemetriaDeVisitas`. Solo
     * reporta: no devuelve nada y no cambia el resultado.
     */
    @VisibleForTesting
    internal val reportarEstado: suspend () -> Unit = {
        EstadoDeEnvioDeVisitas.de(appContext).emitir()
    }
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        reencolarPendientes()
        reconciliar()
        reportarEstadoSinRiesgo()
        return Result.success()
    }

    /** Después de reconciliar, para que los conteos ya digan lo que el servidor confirmó. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun reportarEstadoSinRiesgo() {
        try {
            reportarEstado()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "telemetria de visitas fallo; la corrida sigue", e)
        }
    }

    private suspend fun reconciliar() {
        try {
            trigger()
        } catch (e: Exception) {
            // Defensa: el guard y `ReconcileVisitsUseCase` ya reportan y
            // absorben todo lo esperable (ver KDoc de clase); esto solo cubre
            // un escape genuinamente inesperado. `:app` es legacy y queda
            // fuera de la norma de errores (Ruling I) — Log.w es lo mismo que
            // usa `CobranzaReconcileWorker` al lado.
            Log.w(TAG, "trigger de reconciliacion de visitas fallo de forma inesperada", e)
        }
    }

    /**
     * E-APP-072 (plan visitas P1, Tarea 12; cierra H3, spec §4.2-6): en cada
     * corrida —al abrir la app (`enqueueVisitsReconcileNowWorker`, desde
     * `VisitsReconcileObserver`) y cada 15 min (`enqueueVisitsReconcilePeriodicWorker`)—
     * reencola **todas** las pendientes, sin tope, en orden `FECHA ASC, ID ASC`.
     * `enqueuePendingVisitsWorker` usa `KEEP` y nombre único por id: una visita
     * que ya tiene su trabajo en cola no se duplica. La red recuperada la cubre
     * la restricción `CONNECTED` de cada trabajo encolado.
     *
     * Va ANTES del [trigger] y aislado: una lectura o un encolado que falle no
     * impide reconciliar; lo reintenta el siguiente periodo.
     */
    private suspend fun reencolarPendientes() {
        try {
            pendientes().forEach(encolar)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(
                TAG,
                "reencolado de visitas pendientes falló; lo reintenta el siguiente periodo",
                e
            )
        }
    }

    companion object {
        private const val TAG = "VisitsReconcileWorker"

        /**
         * La lectura por omisión de [pendientes]: todas, `FECHA ASC, ID ASC`, sin
         * tope (E-APP-072). Con nombre para que `VisitasParaElBarridoTest` ejerza
         * esta misma lectura y no una copia.
         */
        @VisibleForTesting
        internal suspend fun pendientesPorOmision(context: Context): List<String> =
            AppDatabase.getInstance(context).visitDao().pendientesOrdenadas()
    }
}
