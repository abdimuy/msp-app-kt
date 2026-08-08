package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.LocalSaleProductEntity

class SaleProductLocalDataSource(context: Context) {
    private val saleProductDao = AppDatabase.getInstance(context).localSaleProduct()

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
