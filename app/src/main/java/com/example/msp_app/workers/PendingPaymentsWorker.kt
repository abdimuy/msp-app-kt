package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.models.payment.toDomain

class PendingPaymentsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val paymentsStore = PaymentsLocalDataSource(appContext)
    private val api = ApiProvider.create(PaymentsApi::class.java)

    override suspend fun doWork(): Result {
        return try {
            Log.d("PendingPaymentsWorker", "Obteniendo pagos pendientes...")
            val pending: List<PaymentEntity> = paymentsStore.getPendingPayments()
            Log.d("PendingPaymentsWorker", "Pagos pendientes: $pending")

            for (payment in pending) {
                api.savePayment(PaymentRequest(payment.toDomain()))
                Log.d("PendingPaymentsWorker", "Pago enviado: ${payment.ID}")
                paymentsStore.changePaymentStatus(payment.ID, true)
                Log.d("PendingPaymentsWorker", "Estado del pago actualizado: ${payment.ID}")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("PendingPaymentsWorker", "Error al enviar pago pendiente", e)
            Result.retry()
        }
    }
}
