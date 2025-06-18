package com.example.msp_app.data.local.dao.product

import androidx.room.*
import com.example.msp_app.data.local.entities.ProductEntity

@Dao
interface ProductDao {

    @Query("SELECT ARTICULO_ID FROM products WHERE ARTICULO_ID = :id")
    suspend fun getProductById(id:Int): Int

    @Query ("SELECT FOLIO FROM products WHERE FOLIO = :saleId")
    suspend fun getProductByFolio(saleId:String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}
