package com.example.msp_app.workmanager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.msp_app.workers.ClienteSyncWorker
import com.example.msp_app.workers.CobranzaReconcileWorker
import com.example.msp_app.workers.PendingGuaranteeEventsWorker
import com.example.msp_app.workers.PendingGuaranteesWorker
import com.example.msp_app.workers.PendingLocalSalesWorker
import com.example.msp_app.workers.PendingPaymentsWorker
import com.example.msp_app.workers.PendingVisitsWorker
import java.util.concurrent.TimeUnit

/**
 * `ExistingWorkPolicy.KEEP` is not a default here — it is the only policy
 * these five pending-work functions know how to produce. `REPLACE` cancels
 * whatever is already running under the same unique name; `KEEP` only ever
 * skips a fresh enqueue while that existing work is still
 * ENQUEUED/RUNNING/BLOCKED, and once it reaches a terminal state
 * (SUCCEEDED/FAILED/CANCELLED) `KEEP` enqueues the new request exactly like
 * `REPLACE` would — so `REPLACE` never rescues anything `KEEP` doesn't
 * already recover for free, and its only real effect is cancelling a live
 * upload and risking a duplicate send. These five cover the money path
 * (payments, visits, guarantees, guarantee events, local sales), so a
 * `replace` parameter that a caller could set to `true` was a standing
 * invitation to reopen that risk — removed instead of merely discouraged.
 * See `SyncAllPendingWorkUseCase` KDoc for the full audit (Task 6).
 */
fun enqueuePendingPaymentsWorker(context: Context, paymentId: String) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf("payment_id" to paymentId)

    val request = OneTimeWorkRequestBuilder<PendingPaymentsWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    val uniqueName = "sync_pending_payments_$paymentId"

    WorkManager.getInstance(context)
        .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
}

fun enqueuePendingVisitsWorker(context: Context, visitId: String) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf("visit_id" to visitId)
    val request = OneTimeWorkRequestBuilder<PendingVisitsWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    val uniqueName = "sync_pending_visit_$visitId"

    WorkManager.getInstance(context)
        .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
}

fun enqueuePendingGuaranteesWorker(context: Context, guaranteeExternalId: String) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf("guarantee_external_id" to guaranteeExternalId)
    val request = OneTimeWorkRequestBuilder<PendingGuaranteesWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    val uniqueName = "sync_pending_guarantee_$guaranteeExternalId"

    WorkManager.getInstance(context)
        .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
}

fun enqueuePendingGuaranteeEventsWorker(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf("event_id" to "batch_events")
    val request = OneTimeWorkRequestBuilder<PendingGuaranteeEventsWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    val uniqueName = "sync_pending_guarantee_events"

    WorkManager.getInstance(context)
        .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
}

fun enqueuePendingLocalSalesWorker(context: Context, localSaleId: String, userEmail: String) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf(
        "local_sale_id" to localSaleId,
        "user_email" to userEmail
    )

    val request = OneTimeWorkRequestBuilder<PendingLocalSalesWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    val uniqueName = "sync_pending_local_sale_$localSaleId"

    WorkManager.getInstance(context)
        .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
}

/** Nombre del trabajo único que reconcilia **ya**, al abrir la app. */
const val COBRANZA_RECONCILE_NOW_WORK = "cobranza_reconcile_now"

/** Nombre del trabajo único que mantiene la cadencia de respaldo. */
const val COBRANZA_RECONCILE_PERIODIC_WORK = "cobranza_reconcile_periodic"

/**
 * Cadencia de respaldo del reconciliador.
 *
 * Son 15 y no 5 minutos porque **15 es el piso de WorkManager**
 * (`PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS`): pedir menos no acorta
 * nada, lo sube en silencio. La cobertura real de los 5 minutos originales la
 * da [enqueueCobranzaReconcileNowWorker], que corre en cada apertura.
 */
const val COBRANZA_RECONCILE_PERIOD_MINUTES = 15L

/**
 * Reconcilia **de inmediato**, sin retraso inicial.
 *
 * El bucle anterior vivía en el ciclo de vida de la UI con el `delay` **antes**
 * de la primera vuelta y moría en `ON_STOP`: exigía cinco minutos seguidos en
 * primer plano cuando el uso real son ráfagas de segundos, así que nunca corrió
 * en la flota. Reconciliar primero y esperar después es el orden, no un detalle.
 *
 * `KEEP` para que abrir y cerrar la app varias veces seguidas no apile
 * corridas: si ya hay una sin terminar, se conserva.
 */
fun enqueueCobranzaReconcileNowWorker(context: Context, replace: Boolean = false) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = OneTimeWorkRequestBuilder<CobranzaReconcileWorker>()
        .setConstraints(constraints)
        .build()

    val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

    WorkManager.getInstance(context)
        .enqueueUniqueWork(COBRANZA_RECONCILE_NOW_WORK, policy, request)
}

/**
 * Cadencia de respaldo del reconciliador, ya fuera del ciclo de vida: sigue
 * corriendo aunque el cobrador cierre la app a los pocos segundos.
 */
fun enqueueCobranzaReconcilePeriodicWorker(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = PeriodicWorkRequestBuilder<CobranzaReconcileWorker>(
        COBRANZA_RECONCILE_PERIOD_MINUTES,
        TimeUnit.MINUTES
    )
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            COBRANZA_RECONCILE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
}

fun enqueueClienteSyncWorker(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = PeriodicWorkRequestBuilder<ClienteSyncWorker>(3, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            "sync_clientes",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
}
