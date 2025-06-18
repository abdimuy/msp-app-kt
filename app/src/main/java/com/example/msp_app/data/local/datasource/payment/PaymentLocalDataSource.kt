package com.example.msp_app.data.local.datasource.payment;

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.dao.payment.PaymentDao
import com.example.msp_app.data.local.entities.PaymentEntity

class PaymentLocalDataSource(context: Context) {
    private val paymentDao = AppDatabase.getInstance(context).paymentDao()

    suspend fun getPaymentById(id:String): String{
        return paymentDao.getPaymentById(id)
    }

    suspend fun getPaymentBySaleId(saleId:Int): List<Int>{
        return paymentDao.getPaymentBySaleId(saleId)
    }

    suspend fun saveAll(payments: List<PaymentEntity>){
        paymentDao.deleteAll()
        paymentDao.saveAll(payments)
    }
}
