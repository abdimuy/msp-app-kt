package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.LocalSaleProductEntity

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
}