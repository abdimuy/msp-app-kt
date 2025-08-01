package com.example.msp_app.data.local.dao.productInventoryImage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.ProductInventoryImageEntity

@Dao
interface ProductInventoryImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ProductInventoryImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllImages(images: List<ProductInventoryImageEntity>)

    @Query(
        """
    SELECT IMAGEN_ID, ARTICULO_ID, RUTA_LOCAL 
    FROM products_inventory_images 
    WHERE ARTICULO_ID = :productId
    """
    )
    suspend fun getImagesByProductId(productId: Int): List<ProductInventoryImageEntity>

    @Query(
        """
    SELECT IMAGEN_ID, ARTICULO_ID, RUTA_LOCAL
    FROM products_inventory_images
    WHERE ARTICULO_ID = :productId
    LIMIT 1
    """
    )
    suspend fun getFirstImageByProductId(productId: Int): ProductInventoryImageEntity?

    @Query(
        """
    SELECT IMAGEN_ID, ARTICULO_ID, RUTA_LOCAL
    FROM products_inventory_images
    """
    )
    suspend fun getAllImages(): List<ProductInventoryImageEntity>

    @Query("DELETE FROM products_inventory_images")
    suspend fun deleteAllImages()

    @Delete
    suspend fun deleteImage(image: ProductInventoryImageEntity)

    @Query("SELECT * FROM products_inventory_images WHERE IMAGEN_ID = :imageId LIMIT 1")
    suspend fun getImageById(imageId: Int): ProductInventoryImageEntity?
}
