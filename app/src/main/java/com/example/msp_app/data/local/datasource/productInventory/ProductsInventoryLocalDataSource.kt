package com.example.msp_app.data.local.datasource.productInventory

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.productInventory.ProductInventoryDao
import com.example.msp_app.core.database.entities.ProductInventoryEntity
import javax.inject.Inject

class ProductInventoryLocalDataSource @Inject constructor(
    private val productInventoryDao: ProductInventoryDao
) {
    /**
     * Puente legacy: `ProductDetailsViewModel` y `ProductsInventoryViewModel`
     * (ViewModels no-Hilt vía `viewModel()`) siguen construyendo con `context`
     * sin cambios. Delega en la MISMA instancia que `@Inject` recibe vía
     * [com.example.msp_app.core.database.di.DatabaseModule] — ambos resuelven
     * a [AppDatabase.getInstance], una sola conexión a `msp_db`. No abre un
     * builder nuevo.
     */
    constructor(context: Context) : this(AppDatabase.getInstance(context).productInventoryDao())

    suspend fun getAll(): List<ProductInventoryEntity> {
        return productInventoryDao.getAll()
    }

    suspend fun getProductInventoryById(id: Int): ProductInventoryEntity {
        return productInventoryDao.getProductInventoryById(id)
    }

    suspend fun getStockById(id: Int): Int? {
        return productInventoryDao.getStockById(id)
    }

    suspend fun insertAll(products: List<ProductInventoryEntity>) {
        productInventoryDao.insertAll(products)
    }

    suspend fun deleteAll() {
        productInventoryDao.deleteAll()
    }
}
