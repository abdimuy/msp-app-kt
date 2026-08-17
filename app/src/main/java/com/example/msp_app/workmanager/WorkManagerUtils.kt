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

fun enqueuePendingPaymentsWorker(context: Context, paymentId: String, replace: Boolean = false) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf("payment_id" to paymentId)

    val request = OneTimeWorkRequestBuilder<PendingPaymentsWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
    val uniqueName = "sync_pending_payments_$paymentId"

    WorkManager.getInstance(context)
        .enqueueUniqueWork(uniqueName, policy, request)
}

fun enqueuePendingVisitsWorker(context: Context, visitId: String, replace: Boolean = false) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf("visit_id" to visitId)
    val request = OneTimeWorkRequestBuilder<PendingVisitsWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
    val uniqueName = "sync_pending_visit_$visitId"

    WorkManager.getInstance(context)
        .enqueueUniqueWork(uniqueName, policy, request)
}

fun enqueuePendingGuaranteesWorker(
    context: Context,
    guaranteeExternalId: String,
    replace: Boolean = false
) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf("guarantee_external_id" to guaranteeExternalId)
    val request = OneTimeWorkRequestBuilder<PendingGuaranteesWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
    val uniqueName = "sync_pending_guarantee_$guaranteeExternalId"

    WorkManager.getInstance(context)
        .enqueueUniqueWork(uniqueName, policy, request)
}

fun enqueuePendingGuaranteeEventsWorker(context: Context, replace: Boolean = false) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf("event_id" to "batch_events")
    val request = OneTimeWorkRequestBuilder<PendingGuaranteeEventsWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
    val uniqueName = "sync_pending_guarantee_events"

    WorkManager.getInstance(context)
        .enqueueUniqueWork(uniqueName, policy, request)
}

fun enqueuePendingLocalSalesWorker(
    context: Context,
    localSaleId: String,
    userEmail: String,
    replace: Boolean = false
) {
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

    val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
    val uniqueName = "sync_pending_local_sale_$localSaleId"

    WorkManager.getInstance(context)
        .enqueueUniqueWork(uniqueName, policy, request)
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
