package com.example.msp_app.data.local.datasource.payment

import android.content.Context
import androidx.room.Transaction
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.models.sale.EstadoCobranza
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker

class PaymentsLocalDataSource(private val context: Context) {
    private val paymentDao = AppDatabase.getInstance(context).paymentDao()
    private val saleDao = AppDatabase.getInstance(context).saleDao()

    suspend fun getPaymentById(id: String): PaymentEntity {
        return paymentDao.getPaymentById(id)
    }

    suspend fun getPaymentsBySaleId(saleId: Int): List<PaymentEntity> {
        return paymentDao.getPaymentsBySaleId(saleId)
    }

    suspend fun getPaymentsByDate(start: String, end: String): List<PaymentEntity> {
        return paymentDao.getPaymentsByDate(start, end)
    }

    suspend fun savePayment(payment: PaymentEntity) {
        paymentDao.savePayment(payment)
    }

    suspend fun saveAll(payments: List<PaymentEntity>) {
        paymentDao.deleteAll()
        paymentDao.saveAll(payments)
    }

    suspend fun getPendingPayments(): List<PaymentEntity> {
        return paymentDao.getPendingPayments()
    }

    suspend fun changePaymentStatus(id: String, status: Boolean) {
        paymentDao.updateEstado(
            id,
            if (status) 1 else 0
        )
    }

    @Transaction
    suspend fun insertPaymentAndUpdateSale(
        payment: PaymentEntity,
        saleId: Int,
        newAmount: Double,
        newEstadoCobranza: EstadoCobranza
    ) {
        paymentDao.savePayment(payment)
        saleDao.updateTotal(saleId, newAmount, newEstadoCobranza)
    }

    suspend fun saveAndEnqueue(
        payment: PaymentEntity,
        saleId: Int,
        newAmount: Double,
        newEstadoCobranza: EstadoCobranza
    ) {
        insertPaymentAndUpdateSale(payment, saleId, newAmount, newEstadoCobranza)
        enqueuePendingPaymentsWorker(context)
    }
}
