package com.example.msp_app.data.local.datasource.payment;

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.PaymentEntity

class PaymentsLocalDataSource(context: Context) {
    private val paymentDao = AppDatabase.getInstance(context).paymentDao()

    suspend fun getPaymentById(id: String): PaymentEntity {
        return paymentDao.getPaymentById(id)
    }

    suspend fun getPaymentsBySaleId(saleId: Int): List<PaymentEntity> {
        return paymentDao.getPaymentsBySaleId(saleId)
    }

    suspend fun saveAll(payments: List<PaymentEntity>) {
        paymentDao.deleteAll()
        paymentDao.saveAll(payments)
    }
}
