package com.example.msp_app.data.local.dao.product

import androidx.room.*
import com.example.msp_app.data.local.entities.ProductEntity

@Dao
interface ProductDao {

    @Query("""SELECT 
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
    WHERE ARTICULO_ID = :id""")
    suspend fun getProductById(id: Int): ProductEntity

    @Query("""SELECT 
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
    WHERE FOLIO = :folio""")
    suspend fun getProductsByFolio(folio: String): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}
