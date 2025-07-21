package com.example.msp_app.data.local.dao.sale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.SaleEntity
import com.example.msp_app.data.local.entities.SaleWithProductsEntity
import com.example.msp_app.data.models.sale.EstadoCobranza

@Dao
interface SaleDao {

    @Query(
        """
        SELECT
            DOCTO_CC_ACR_ID,
            DOCTO_CC_ID,
            sales.FOLIO,
            CLIENTE_ID,
            APLICADO,
            COBRADOR_ID,
            CLIENTE,
            ZONA_CLIENTE_ID,
            LIMITE_CREDITO,
            NOTAS,
            ZONA_NOMBRE,
            IMPORTE_PAGO_PROMEDIO,
            TOTAL_IMPORTE,
            NUM_IMPORTES,
            FECHA,
            PARCIALIDAD,
            ENGANCHE,
            TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO,
            VENDEDOR_1,
            VENDEDOR_2,
            VENDEDOR_3,
            PRECIO_TOTAL,
            IMPTE_REST,
            SALDO_REST,
            FECHA_ULT_PAGO,
            CALLE,
            CIUDAD,
            ESTADO,
            TELEFONO,
            NOMBRE_COBRADOR,
            ESTADO_COBRANZA,
            DIA_COBRANZA,
            DIA_TEMPORAL_COBRANZA,
            PRECIO_DE_CONTADO,
            AVAL_O_RESPONSABLE,
            FREC_PAGO,
            GROUP_CONCAT(p.ARTICULO, ', ') AS PRODUCTOS
        FROM sales
        LEFT JOIN products p ON p.FOLIO = sales.FOLIO
        GROUP BY sales.DOCTO_CC_ID;
    """
    )
    suspend fun getAll(): List<SaleWithProductsEntity>

    @Query(
        """
        SELECT
            DOCTO_CC_ACR_ID,
            DOCTO_CC_ID,
            sales.FOLIO,
            CLIENTE_ID,
            APLICADO,
            COBRADOR_ID,
            CLIENTE,
            ZONA_CLIENTE_ID,
            LIMITE_CREDITO,
            NOTAS,
            ZONA_NOMBRE,
            IMPORTE_PAGO_PROMEDIO,
            TOTAL_IMPORTE,
            NUM_IMPORTES,
            FECHA,
            PARCIALIDAD,
            ENGANCHE,
            TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO,
            VENDEDOR_1,
            VENDEDOR_2,
            VENDEDOR_3,
            PRECIO_TOTAL,
            IMPTE_REST,
            SALDO_REST,
            FECHA_ULT_PAGO,
            CALLE,
            CIUDAD,
            ESTADO,
            TELEFONO,
            NOMBRE_COBRADOR,
            ESTADO_COBRANZA,
            DIA_COBRANZA,
            DIA_TEMPORAL_COBRANZA,
            PRECIO_DE_CONTADO,
            AVAL_O_RESPONSABLE,
            FREC_PAGO
        FROM sales
        WHERE DOCTO_CC_ACR_ID = :id
    """
    )
    suspend fun getById(id: Int): SaleEntity?

    @Query(
        """
        SELECT
            DOCTO_CC_ACR_ID,
            DOCTO_CC_ID,
            sales.FOLIO,
            CLIENTE_ID,
            APLICADO,
            COBRADOR_ID,
            CLIENTE,
            ZONA_CLIENTE_ID,
            LIMITE_CREDITO,
            NOTAS,
            ZONA_NOMBRE,
            IMPORTE_PAGO_PROMEDIO,
            TOTAL_IMPORTE,
            NUM_IMPORTES,
            FECHA,
            PARCIALIDAD,
            ENGANCHE,
            TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO,
            VENDEDOR_1,
            VENDEDOR_2,
            VENDEDOR_3,
            PRECIO_TOTAL,
            IMPTE_REST,
            SALDO_REST,
            FECHA_ULT_PAGO,
            CALLE,
            CIUDAD,
            ESTADO,
            TELEFONO,
            NOMBRE_COBRADOR,
            ESTADO_COBRANZA,
            DIA_COBRANZA,
            DIA_TEMPORAL_COBRANZA,
            PRECIO_DE_CONTADO,
            AVAL_O_RESPONSABLE,
            FREC_PAGO,
            GROUP_CONCAT(p.ARTICULO, ', ') AS PRODUCTOS
        FROM sales
        LEFT JOIN products p ON p.FOLIO = sales.FOLIO
        WHERE sales.CLIENTE_ID = :clientId
        GROUP BY sales.DOCTO_CC_ID
        """
    )
    suspend fun getByClientId(clientId: Int): List<SaleWithProductsEntity>

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

    @Query(
        """
        UPDATE sales
        SET 
            DIA_TEMPORAL_COBRANZA = :newDate
        WHERE 
            DOCTO_CC_ACR_ID = :saleId
    """
    )
    suspend fun updateTemporaryCollectionDate(saleId: Int, newDate: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sales: List<SaleEntity>)

    @Query("DELETE FROM sales")
    suspend fun clearAll()
}
