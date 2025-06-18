package com.example.msp_app.data.local.dao.payment;

import androidx.room.*
import com.example.msp_app.data.local.entities.PaymentEntity

@Dao
interface PaymentDao {

    @Query("SELECT * FROM payment WHERE DOCTO_CC_ID = :saleId AND ID = :paymentId")
    suspend fun getPaymentBySaleIdAndId(saleId: Int, paymentId: Int): List<PaymentEntity>

    @Insert (onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payment: List<PaymentEntity>)

    @Query ("DELETE FROM payment")
    suspend fun clearAll()
}
