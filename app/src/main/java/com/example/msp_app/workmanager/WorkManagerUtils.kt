package com.example.msp_app.workmanager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.msp_app.workers.PendingPaymentsWorker

fun enqueuePendingPaymentsWorker(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = OneTimeWorkRequestBuilder<PendingPaymentsWorker>()
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            "sync_pending_payments",
            ExistingWorkPolicy.KEEP,
            request
        )
}
