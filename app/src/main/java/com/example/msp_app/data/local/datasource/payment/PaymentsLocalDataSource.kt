package com.example.msp_app.data.local.datasource.payment;

import android.content.Context
import androidx.room.Transaction
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.models.sale.EstadoCobranza

class PaymentsLocalDataSource(context: Context) {
    private val paymentDao = AppDatabase.getInstance(context).paymentDao()
    private val saleDao = AppDatabase.getInstance(context).saleDao()

    suspend fun getPaymentById(id: String): PaymentEntity {
        return paymentDao.getPaymentById(id)
    }

    suspend fun getPaymentsBySaleId(saleId: Int): List<PaymentEntity> {
        return paymentDao.getPaymentsBySaleId(saleId)
    }

    suspend fun savePayment(payment: PaymentEntity) {
        paymentDao.savePayment(payment)
    }

    suspend fun saveAll(payments: List<PaymentEntity>) {
        paymentDao.deleteAll()
        paymentDao.saveAll(payments)
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
}
