package com.example.msp_app.data.local.dao.sale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.SaleEntity
import com.example.msp_app.data.local.entities.SaleWithProductsEntity
import com.example.msp_app.data.models.sale.EstadoCobranza
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {

    @Query(
        """
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
        LEFT JOIN overdue_payments_view AS a ON a.DOCTO_CC_ID = s.DOCTO_CC_ID
        GROUP BY s.DOCTO_CC_ID
"""
    )
    suspend fun getAll(): List<SaleWithProductsEntity>

    /**
     * Same shape as [getAll] but Room-reactive: re-emits on every write to
     * `sales`, `products` o `overdue_payments_view`. Sirve para que la UI
     * repinte sola cuando el sync incremental de cobranza escribe rows.
     *
     * Sin filtro de saldo — el backend ya devuelve solo ventas con
     * `SALDO > 0` o tombstones (`CARGO_CANCELADO = 'S'`), asi que la
     * tabla local refleja el set que la ruta debe mostrar.
     */
    @Query(
        """
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
        LEFT JOIN overdue_payments_view AS a ON a.DOCTO_CC_ID = s.DOCTO_CC_ID
        GROUP BY s.DOCTO_CC_ID
        """
    )
    fun observeAll(): Flow<List<SaleWithProductsEntity>>

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

    @Query(
        """
        SELECT
            DOCTO_CC_ACR_ID, DOCTO_CC_ID, sales.FOLIO, CLIENTE_ID, APLICADO,
            COBRADOR_ID, CLIENTE, ZONA_CLIENTE_ID, LIMITE_CREDITO, NOTAS,
            ZONA_NOMBRE, IMPORTE_PAGO_PROMEDIO, TOTAL_IMPORTE, NUM_IMPORTES,
            FECHA, PARCIALIDAD, ENGANCHE, TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO, VENDEDOR_1, VENDEDOR_2, VENDEDOR_3,
            PRECIO_TOTAL, IMPTE_REST, SALDO_REST, FECHA_ULT_PAGO,
            CALLE, CIUDAD, ESTADO, TELEFONO, NOMBRE_COBRADOR,
            ESTADO_COBRANZA, DIA_COBRANZA, DIA_TEMPORAL_COBRANZA,
            PRECIO_DE_CONTADO, AVAL_O_RESPONSABLE, FREC_PAGO
        FROM sales
        WHERE DOCTO_CC_ID = :doctoCcId
        """
    )
    suspend fun findByDoctoCcId(doctoCcId: Int): SaleEntity?

    @Query("DELETE FROM sales WHERE DOCTO_CC_ID = :doctoCcId")
    suspend fun deleteByDoctoCcId(doctoCcId: Int)

    /**
     * Cuenta cuántas ventas en local pertenecen a una zona distinta a la
     * indicada. Sirve como detector de residuos de otra zona — útil cuando
     * el state de sync apunta correctamente a la zona actual pero la tabla
     * todavía contiene rows viejos por una transición pasada.
     */
    @Query("SELECT COUNT(*) FROM sales WHERE ZONA_CLIENTE_ID != :zonaActual")
    suspend fun countByZonaIdNot(zonaActual: Int): Int

    /**
     * Borra las ventas saldadas (`SALDO_REST <= 0`) cuyos pagos quedaron
     * todos fuera de la ventana del cobrador. Conserva las saldadas con
     * al menos un pago dentro de la ventana — el cobrador necesita verlas
     * mientras la fecha de inicio (FECHA_CARGA_INICIAL) abarque ese pago.
     *
     * No toca la tabla `Payment` — el histórico de pagos se conserva por
     * normativa SAT y para los reportes diario/semanal, incluso después
     * de que la venta deja de mostrarse en la ruta.
     *
     * Retorna el número de filas eliminadas.
     */
    @Query(
        """
        DELETE FROM sales
        WHERE SALDO_REST <= 0
          AND NOT EXISTS (
            SELECT 1 FROM Payment
            WHERE Payment.DOCTO_CC_ACR_ID = sales.DOCTO_CC_ID
              AND Payment.FECHA_HORA_PAGO >= :fechaIso
          )
        """
    )
    suspend fun deleteSaldadasFueraDeVentana(fechaIso: String): Int

    /**
     * Devuelve los DOCTO_CC_IDs de TODAS las ventas en local para esta zona.
     * Usado por CobranzaReconciler para computar el digest local y detectar
     * phantoms (rows que el server ya no tiene activas).
     *
     * Los filtros del server /digest y /ids están alineados con /sync
     * (CARGO_CANCELADO='N' + SALDO > 0 + opcional desde). En steady state
     * `extras` debe ser 0 — si persiste, indica una divergencia de filtros
     * server-side. La query local no filtra por SALDO porque las ventas
     * saldadas se pruenan localmente por pruneSaldadasFueraDeVentana;
     * filtrar aquí produciría falsos phantoms durante ese intervalo.
     */
    @Query("SELECT DOCTO_CC_ID FROM sales WHERE ZONA_CLIENTE_ID = :zonaId ORDER BY DOCTO_CC_ID ASC")
    suspend fun getActiveIdsByZona(zonaId: Int): List<Int>

    /**
     * Bulk delete por PK. Idempotente: si una de las PK no existe, simplemente
     * no la borra. Usado por CobranzaReconciler para evictar phantoms.
     */
    @Query("DELETE FROM sales WHERE DOCTO_CC_ID IN (:doctoCcIds)")
    suspend fun deleteByDoctoCcIds(doctoCcIds: List<Int>)

    @Query("DELETE FROM sales")
    suspend fun deleteAll()
}
