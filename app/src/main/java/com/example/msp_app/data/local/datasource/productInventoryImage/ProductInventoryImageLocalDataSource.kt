package com.example.msp_app.data.local.datasource.productInventoryImage

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.dao.productInventory.ProductInventoryDao
import com.example.msp_app.data.local.entities.ProductInventoryImageEntity

class ProductInventoryImageLocalDataSource(
    context: Context,
    private val productDao: ProductInventoryDao
) {

    private val productInventoryImageDao =
        AppDatabase.getInstance(context).productInventoryImageDao()

    suspend fun insertImage(image: ProductInventoryImageEntity) {
        productInventoryImageDao.insertImage(image)
    }

    suspend fun insertAllImages(images: List<ProductInventoryImageEntity>) {
        productInventoryImageDao.insertAllImages(images)
    }

    suspend fun getImagesByProductId(productId: Int): List<ProductInventoryImageEntity> {
        return productInventoryImageDao.getImagesByProductId(productId)
    }

    suspend fun getFirstImageByProductId(productId: Int): ProductInventoryImageEntity? {
        return productInventoryImageDao.getFirstImageByProductId(productId)
    }

    suspend fun getAllImages(): List<ProductInventoryImageEntity> {
        return productInventoryImageDao.getAllImages()
    }

    suspend fun getImageById(imageId: Int): ProductInventoryImageEntity? {
        return productInventoryImageDao.getImageById(imageId)
    }

    suspend fun existsByProductId(id: Int): Boolean {
        return productDao.existsById(id)
    }

    suspend fun insertSafeImages(
        images: List<ProductInventoryImageEntity>,
    ) {
        val validImages = images.filter { image ->
            productDao.existsById(image.ARTICULO_ID)
        }
        productInventoryImageDao.insertAllImages(validImages)
    }

    suspend fun deleteAllImages() {
        productInventoryImageDao.deleteAllImages()
    }

    suspend fun deleteImage(image: ProductInventoryImageEntity) {
        productInventoryImageDao.deleteImage(image)
    }
}
