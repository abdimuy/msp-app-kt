package com.example.msp_app.data.local.dao.product

import androidx.room.*
import com.example.msp_app.data.local.entities.ProductEntity

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE DOCTO_PV_ID = :saleId AND ARTICULO_ID = :productId")
    suspend fun getProductBySaleIdAndProductId(saleId: Int, productId: Int): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun clearAll()
}
