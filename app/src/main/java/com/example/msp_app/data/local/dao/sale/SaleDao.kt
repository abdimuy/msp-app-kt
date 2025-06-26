package com.example.msp_app.data.local.dao.sale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.SaleEntity
import com.example.msp_app.data.models.sale.EstadoCobranza

@Dao
interface SaleDao {

    @Query("SELECT * FROM sales")
    suspend fun getAll(): List<SaleEntity>

    @Query(
        """
    UPDATE sales 
    SET 
        SALDO_REST = SALDO_REST - :amount, 
        ESTADO_COBRANZA = :estadoCobranza 
    WHERE 
        DOCTO_CC_ACR_ID = :saleId
    """
    )
    suspend fun updateTotal(saleId: Int, amount: Double, estadoCobranza: EstadoCobranza)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sales: List<SaleEntity>)

    @Query("DELETE FROM sales")
    suspend fun clearAll()
}
