package com.example.msp_app.data.local.datasource.productInventory

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.ProductInventoryEntity

class ProductInventoryLocalDataSource(private val context: Context) {

    private val productInventoryDao = AppDatabase.getInstance(context).productInventoryDao()

    suspend fun getAll(): List<ProductInventoryEntity> {
        return productInventoryDao.getAll()
    }

    suspend fun getProductInventoryById(id: Int): ProductInventoryEntity {
        return productInventoryDao.getProductInventoryById(id)
    }

    suspend fun insertAll(products: List<ProductInventoryEntity>) {
        productInventoryDao.insertAll(products)
    }

    suspend fun deleteAll() {
        productInventoryDao.deleteAll()
    }
}
