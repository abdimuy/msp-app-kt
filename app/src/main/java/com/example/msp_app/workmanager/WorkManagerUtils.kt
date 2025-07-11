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

fun enqueuePendingPaymentsWorker(context: Context, paymentId: String) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf("payment_id" to paymentId)

    val request = OneTimeWorkRequestBuilder<PendingPaymentsWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            "sync_pending_payments",
            ExistingWorkPolicy.KEEP,
            request
        )
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

    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            "sync_pending_visits",
            ExistingWorkPolicy.KEEP,
            request
        )
}