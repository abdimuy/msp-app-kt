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
import com.example.msp_app.workers.PendingPaymentsWorker
import com.example.msp_app.workers.PendingVisitsWorker
import com.example.msp_app.workers.PendingGuaranteesWorker
import com.example.msp_app.workers.PendingGuaranteeEventsWorker
import com.example.msp_app.workers.PendingLocalSalesWorker
import java.util.concurrent.TimeUnit

fun enqueuePendingPaymentsWorker(
    context: Context,
    paymentId: String,
    replace: Boolean = false
) {
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

fun enqueuePendingVisitsWorker(
    context: Context,
    visitId: String,
    replace: Boolean = false
) {
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

fun enqueuePendingGuaranteeEventsWorker(
    context: Context,
    replace: Boolean = false
) {
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

fun enqueueClienteSyncWorker(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = PeriodicWorkRequestBuilder<ClienteSyncWorker>(24, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            "sync_clientes",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
}