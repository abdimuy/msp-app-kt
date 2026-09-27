package com.example.msp_app.core.database.dao.product

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.ProductEntity

@Dao
interface ProductDao {

    /**
     * Los artículos de **todas las ventas de un cliente**, por `DOCTO_CC_ACR_ID`
     * (la llave con la que viaja cada abono), en el orden de la venta.
     *
     * Lo lee el mapa de lugares para decir "de qué venta" se pagó en cada lugar.
     * Sólo lectura, sobre tablas y columnas existentes: no toca el schema.
     */
    @Query(
        """SELECT s.DOCTO_CC_ACR_ID AS ventaId, p.ARTICULO AS articulo
        FROM sales AS s
        JOIN products AS p ON p.FOLIO = s.FOLIO
        WHERE s.CLIENTE_ID = :clienteId
        ORDER BY s.DOCTO_CC_ACR_ID, p.POSICION"""
    )
    suspend fun getArticulosDeLasVentasDelCliente(clienteId: Int): List<ArticuloDeVentaRow>

    @Query(
        """SELECT 
        DOCTO_PV_DET_ID, 
        DOCTO_PV_ID, 
        FOLIO, 
        ARTICULO_ID, 
        ARTICULO, 
        CANTIDAD, 
        PRECIO_UNITARIO_IMPTO, 
        PRECIO_TOTAL_NETO, 
        POSICION 
    FROM products 
    WHERE ARTICULO_ID = :id"""
    )
    suspend fun getProductById(id: Int): ProductEntity?

    @Query(
        """SELECT 
        DOCTO_PV_DET_ID, 
        DOCTO_PV_ID, 
        FOLIO, 
        ARTICULO_ID, 
        ARTICULO, 
        CANTIDAD, 
        PRECIO_UNITARIO_IMPTO, 
        PRECIO_TOTAL_NETO, 
        POSICION
    FROM products
    WHERE FOLIO = :folio"""
    )
    suspend fun getProductsByFolio(folio: String): List<ProductEntity>

    /**
     * Los renglones de VARIOS folios en una sola consulta (`WHERE FOLIO IN`),
     * para el llamador que necesita los productos de TODAS las ventas de un
     * cliente y no de una sola — ver el KDoc de
     * `com.example.msp_app.feature.pagos.data.adapter.RoomProductosAdapter.productosDeVarios`
     * para el porqué y el troceo por el tope de SQLite.
     */
    @Query(
        """SELECT
        DOCTO_PV_DET_ID,
        DOCTO_PV_ID,
        FOLIO,
        ARTICULO_ID,
        ARTICULO,
        CANTIDAD,
        PRECIO_UNITARIO_IMPTO,
        PRECIO_TOTAL_NETO,
        POSICION
    FROM products
    WHERE FOLIO IN (:folios)"""
    )
    suspend fun getProductsByFolios(folios: List<String>): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("DELETE FROM products WHERE FOLIO = :folio")
    suspend fun deleteByFolio(folio: String)
}

/** Una fila de [ProductDao.getArticulosDeLasVentasDelCliente]. */
data class ArticuloDeVentaRow(val ventaId: Int, val articulo: String)
