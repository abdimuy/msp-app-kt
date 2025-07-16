package com.example.msp_app.workmanager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.msp_app.workers.PendingPaymentsWorker
import com.example.msp_app.workers.PendingVisitsWorker

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