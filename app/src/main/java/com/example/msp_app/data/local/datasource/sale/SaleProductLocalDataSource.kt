package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.localsale.LocalSaleProductDao
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import javax.inject.Inject

class SaleProductLocalDataSource @Inject constructor(
    private val saleProductDao: LocalSaleProductDao
) {
    /**
     * Puente legacy: ViewModels no-Hilt y `PendingLocalSalesWorker` (V1, aún
     * no `@HiltWorker`) siguen construyendo con `context` sin cambios.
     * Delega en la MISMA instancia que `@Inject` recibe vía
     * [com.example.msp_app.core.database.di.DatabaseModule] — ambos
     * resuelven a [AppDatabase.getInstance], una sola conexión a `msp_db`.
     * No abre un builder nuevo: eso duplicaría la ruta de escritura al
     * dinero.
     *
     * Nombre exacto del método en [AppDatabase]: `localSaleProduct()`, NO
     * `localSaleProductDao()`.
     */
    constructor(context: Context) : this(AppDatabase.getInstance(context).localSaleProduct())

    suspend fun insertSaleProduct(product: LocalSaleProductEntity) {
        saleProductDao.insertSaleProduct(product)
    }

    suspend fun insertSaleProducts(products: List<LocalSaleProductEntity>) {
        saleProductDao.insertAllSaleProducts(products)
    }

    suspend fun getProductsForSale(saleId: String): List<LocalSaleProductEntity> {
        return try {
            saleProductDao.getProductsForSale(saleId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteProductsForSale(saleId: String) {
        saleProductDao.deleteProductsForSale(saleId)
    }

    suspend fun updateServerUuid(saleId: String, articuloId: Int, serverUuid: String) {
        saleProductDao.updateServerUuid(saleId, articuloId, serverUuid)
    }
}
