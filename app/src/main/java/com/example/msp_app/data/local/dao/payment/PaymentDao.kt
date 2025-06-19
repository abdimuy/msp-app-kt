package com.example.msp_app.data.local.dao.payment;

import androidx.room.*
import com.example.msp_app.data.local.entities.PaymentEntity

@Dao
interface PaymentDao {

    @Query("""SELECT ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO, GUARDADO_EN_MICROSIP,
        IMPORTE, LAT, LNG, CLIENTE_ID, COBRADOR_ID, FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE
    FROM Payment WHERE ID = :id""")
    suspend fun getPaymentById(id:String): PaymentEntity

    @Query("""SELECT ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO, GUARDADO_EN_MICROSIP,
        IMPORTE, LAT, LNG, CLIENTE_ID, COBRADOR_ID, FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE
    FROM Payment WHERE DOCTO_CC_ACR_ID = :saleId""")
    suspend fun getPaymentBySalesId(saleId:Int): List<PaymentEntity>

    @Insert (onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(payment: List<PaymentEntity>)

    @Query ("DELETE FROM payment")
    suspend fun deleteAll()
}
