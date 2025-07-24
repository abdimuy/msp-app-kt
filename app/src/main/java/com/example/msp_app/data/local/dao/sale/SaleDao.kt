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
        WITH atrasos AS (
            SELECT
        sales.DOCTO_CC_ID,
        sales.FECHA_ULT_PAGO,
        sales.NUM_IMPORTES,
        sales.PARCIALIDADES_TRANSCURRIDAS,
        CASE 
            WHEN ((sales.PARCIALIDADES_TRANSCURRIDAS * sales.PARCIALIDAD 
                  - (sales.PRECIO_TOTAL - sales.SALDO_REST)) / sales.PARCIALIDAD) 
                > (sales.SALDO_REST / sales.PARCIALIDAD)
            THEN (sales.SALDO_REST / sales.PARCIALIDAD)
            ELSE ((sales.PARCIALIDADES_TRANSCURRIDAS * sales.PARCIALIDAD 
                  - (sales.PRECIO_TOTAL - sales.SALDO_REST - sales.ENGANCHE)) / sales.PARCIALIDAD)
        END AS NUM_PAGOS_ATRASADOS
    FROM (
        SELECT
            s.DOCTO_CC_ID,
            COALESCE(MAX(p.FECHA_HORA_PAGO), DATE('now')) AS FECHA_ULT_PAGO,
            COALESCE(COUNT(p.FECHA_HORA_PAGO), 0) AS NUM_IMPORTES,
            COALESCE(SUM(p.IMPORTE), 0) AS TOTAL_IMPORTE,
            s.FREC_PAGO,
            s.SALDO_REST,
            s.PRECIO_TOTAL,
            s.PARCIALIDAD,
            s.ENGANCHE,
            (
                JULIANDAY(
                    CASE
                        WHEN s.SALDO_REST = 0 THEN MAX(p.FECHA_HORA_PAGO)
                        ELSE DATE('now')
                    END
                ) - JULIANDAY(s.FECHA)
            ) / CASE 
                WHEN s.FREC_PAGO = 'SEMANAL' THEN 7
                WHEN s.FREC_PAGO = 'QUINCENAL' THEN 15
                WHEN s.FREC_PAGO = 'MENSUAL' THEN 30
                ELSE 1
            END AS PARCIALIDADES_TRANSCURRIDAS
        FROM sales AS s
        LEFT JOIN payment AS p ON s.DOCTO_CC_ID = p.DOCTO_CC_ACR_ID
        GROUP BY s.DOCTO_CC_ID, s.FREC_PAGO
    ) AS sales
        )
        SELECT
            s.DOCTO_CC_ACR_ID,
            s.DOCTO_CC_ID,
            s.FOLIO,
            s.CLIENTE_ID,
            s.APLICADO,
            s.COBRADOR_ID,
            s.CLIENTE,
            s.ZONA_CLIENTE_ID,
            s.LIMITE_CREDITO,
            s.NOTAS,
            s.ZONA_NOMBRE,
            s.IMPORTE_PAGO_PROMEDIO,
            s.TOTAL_IMPORTE,
            s.NUM_IMPORTES,
            s.FECHA,
            s.PARCIALIDAD,
            s.ENGANCHE,
            s.TIEMPO_A_CORTO_PLAZOMESES,
            s.MONTO_A_CORTO_PLAZO,
            s.VENDEDOR_1,
            s.VENDEDOR_2,
            s.VENDEDOR_3,
            s.PRECIO_TOTAL,
            s.IMPTE_REST,
            s.SALDO_REST,
            s.FECHA_ULT_PAGO,
            s.CALLE,
            s.CIUDAD,
            s.ESTADO,
            s.TELEFONO,
            s.NOMBRE_COBRADOR,
            s.ESTADO_COBRANZA,
            s.DIA_COBRANZA,
            s.DIA_TEMPORAL_COBRANZA,
            s.PRECIO_DE_CONTADO,
            s.AVAL_O_RESPONSABLE,
            s.FREC_PAGO,
            GROUP_CONCAT(p.ARTICULO, ', ') AS PRODUCTOS,
            CAST(a.NUM_PAGOS_ATRASADOS AS INTEGER) AS NUM_PAGOS_ATRASADOS
        FROM sales AS s
        LEFT JOIN products AS p ON p.FOLIO = s.FOLIO
        LEFT JOIN atrasos AS a ON a.DOCTO_CC_ID = s.DOCTO_CC_ID
        GROUP BY s.DOCTO_CC_ID
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
