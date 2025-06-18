package com.example.msp_app.data.local.dao.payment;

import androidx.room.*
import com.example.msp_app.data.local.entities.PaymentEntity

@Dao
interface PaymentDao {

    @Query ("SELECT ID FROM payment WHERE ID = :id")
    suspend fun getPaymentById(id:String): String

    @Query ("SELECT DOCTO_CC_ACR_ID FROM payment WHERE DOCTO_CC_ACR_ID = :saleId")
    suspend fun getPaymentBySaleId(saleId:Int): List<Int>

    @Insert (onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(payment: List<PaymentEntity>)

    @Query ("DELETE FROM payment")
    suspend fun deleteAll()
}
