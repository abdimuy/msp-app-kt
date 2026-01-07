package com.example.msp_app.data.local.dao.localsale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.LocalSaleProductEntity

@Dao
interface LocalSaleProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleProduct(saleProduct: LocalSaleProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSaleProducts(saleProducts: List<LocalSaleProductEntity>)

    @Query("SELECT LOCAL_SALE_ID, ARTICULO_ID, ARTICULO, CANTIDAD, PRECIO_LISTA, PRECIO_CORTO_PLAZO, PRECIO_CONTADO, COMBO_ID FROM local_sale_products WHERE LOCAL_SALE_ID = :saleId")
    suspend fun getProductsForSale(saleId: String): List<LocalSaleProductEntity>

    @Query("DELETE FROM local_sale_products WHERE LOCAL_SALE_ID = :saleId")
    suspend fun deleteProductsForSale(saleId: String)
}