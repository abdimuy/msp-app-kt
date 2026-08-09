package com.example.msp_app.data.local.datasource.productInventoryImage

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.productInventory.ProductInventoryDao
import com.example.msp_app.core.database.dao.productInventoryImage.ProductInventoryImageDao
import com.example.msp_app.core.database.entities.ProductInventoryImageEntity
import javax.inject.Inject

class ProductInventoryImageLocalDataSource @Inject constructor(
    private val productInventoryImageDao: ProductInventoryImageDao,
    private val productDao: ProductInventoryDao
) {
    /**
     * Puente legacy: `ProductsInventoryImagesViewModel` (ViewModel no-Hilt vía
     * `viewModel()`) sigue construyendo con `context` + `productDao` explícito
     * (este último aún resuelto por el propio ViewModel vía `getInstance` —
     * residual documentado para Task 9). Delega en la MISMA instancia que
     * `@Inject` recibe vía [com.example.msp_app.core.database.di.DatabaseModule]
     * — ambos resuelven a [AppDatabase.getInstance], una sola conexión a
     * `msp_db`. No abre un builder nuevo.
     */
    constructor(context: Context, productDao: ProductInventoryDao) : this(
        AppDatabase.getInstance(context).productInventoryImageDao(),
        productDao
    )

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

    suspend fun getAllProducts() = productDao.getAll()

    suspend fun insertSafeImages(images: List<ProductInventoryImageEntity>) {
        productInventoryImageDao.insertAllImages(images)
    }

    suspend fun deleteAllImages() {
        productInventoryImageDao.deleteAllImages()
    }

    suspend fun deleteImage(image: ProductInventoryImageEntity) {
        productInventoryImageDao.deleteImage(image)
    }
}
