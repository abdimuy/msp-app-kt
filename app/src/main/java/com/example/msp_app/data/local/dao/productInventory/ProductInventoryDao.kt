package com.example.msp_app.data.local.dao.productInventory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.ProductInventoryEntity

@Dao
interface ProductInventoryDao {
    @Query(
        """
        SELECT 
            ARTICULO_ID, 
            ARTICULO, 
            EXISTENCIAS, 
            LINEA_ARTICULO_ID, 
            LINEA_ARTICULO, 
            PRECIOS 
        FROM product_inventory
    """
    )
    suspend fun getAll(): List<ProductInventoryEntity>

    @Query(
        """
        SELECT 
            ARTICULO_ID, 
            ARTICULO, 
            EXISTENCIAS, 
            LINEA_ARTICULO_ID, 
            LINEA_ARTICULO, 
            PRECIOS 
        FROM product_inventory 
        WHERE ARTICULO_ID = :id 
        LIMIT 1
    """
    )
    suspend fun getProductInventoryById(id: Int): ProductInventoryEntity

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductInventoryEntity>)

    @Query("DELETE FROM product_inventory")
    suspend fun deleteAll()
}