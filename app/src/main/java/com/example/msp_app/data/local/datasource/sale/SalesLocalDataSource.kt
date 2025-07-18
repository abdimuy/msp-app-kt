package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.SaleEntity
import com.example.msp_app.data.local.entities.SaleWithProductsEntity

class SalesLocalDataSource(context: Context) {
    private val saleDao = AppDatabase.getInstance(context).saleDao()

    suspend fun getAll(): List<SaleWithProductsEntity> = saleDao.getAll()

    suspend fun getByClientId(clientId: Int): List<SaleWithProductsEntity> {
        return saleDao.getByClientId(clientId)
    }

    suspend fun saveAll(sales: List<SaleEntity>) {
        saleDao.clearAll()
        saleDao.insertAll(sales)
    }

    suspend fun getById(id: Int): SaleEntity? {
        return saleDao.getById(id)
    }
}