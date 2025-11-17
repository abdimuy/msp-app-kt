package com.example.msp_app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.repository.PaymentsRepository
import com.example.msp_app.data.models.auth.User
import com.google.gson.Gson

class PaymentsExportWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val userJson = inputData.getString("user_json")
        val user = Gson().fromJson(userJson, User::class.java)

        val repository = PaymentsRepository(
            PaymentsLocalDataSource(applicationContext),
            AppDatabase.getInstance(applicationContext).saleDao()
        )

        repository.exportPaymentsJsonWithSales(user)
        return Result.success()
    }
}
