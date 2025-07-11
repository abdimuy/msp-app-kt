package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.payment.toDomain

class PendingPaymentsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val paymentsStore = PaymentsLocalDataSource(appContext)
    private val api = ApiProvider.create(PaymentsApi::class.java)

    override suspend fun doWork(): Result {
        val id = inputData.getString("payment_id")
            ?: return Result.failure().also {
                Log.e("PendingPaymentsWorker", "No se proporcionó payment_id")
            }

        val payment = paymentsStore.getPaymentById(id)
            ?: return Result.failure().also {
                Log.e("PendingPaymentsWorker", "Pago no encontrado: $id")
            }

        return try {
            Log.d("PendingPaymentsWorker", "Enviando pago: ${payment.ID}")
            api.savePayment(PaymentRequest(payment.toDomain()))
            paymentsStore.changePaymentStatus(payment.ID, true)
            Log.d("PendingPaymentsWorker", "Pago marcado como enviado: ${payment.ID}")
            Result.success()
        } catch (e: Exception) {
            Log.e("PendingPaymentsWorker", "Error al enviar pago ${payment.ID}", e)
            Result.retry()
        }
    }
}