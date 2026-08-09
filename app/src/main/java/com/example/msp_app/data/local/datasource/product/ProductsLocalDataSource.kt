package com.example.msp_app.data.local.datasource.product

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.product.ProductDao
import com.example.msp_app.core.database.entities.ProductEntity
import javax.inject.Inject

class ProductsLocalDataSource @Inject constructor(
    private val productDao: ProductDao
) {
    /**
     * Puente legacy: `ProductsViewModel`, `PaymentsViewModel` y `SalesViewModel`
     * (ViewModels no-Hilt vía `viewModel()`) siguen construyendo con `context`
     * sin cambios. Delega en la MISMA instancia que `@Inject` recibe vía
     * [com.example.msp_app.core.database.di.DatabaseModule] — ambos resuelven
     * a [AppDatabase.getInstance], una sola conexión a `msp_db`. No abre un
     * builder nuevo.
     */
    constructor(context: Context) : this(AppDatabase.getInstance(context).productDao())

    suspend fun getProductById(id: Int): ProductEntity? {
        return productDao.getProductById(id)
    }

    suspend fun getProductsByFolio(folio: String): List<ProductEntity> {
        return productDao.getProductsByFolio(folio)
    }

    suspend fun saveAll(products: List<ProductEntity>) {
        productDao.deleteAll()
        productDao.saveAll(products)
    }
}
