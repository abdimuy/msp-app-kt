package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.SaleEntity
import com.example.msp_app.data.local.entities.SaleWithProductsEntity
import kotlinx.coroutines.flow.Flow

class SalesLocalDataSource(context: Context) {
    private val saleDao = AppDatabase.getInstance(context).saleDao()

    suspend fun getAll(): List<SaleWithProductsEntity> = saleDao.getAll()

    fun observeAll(): Flow<List<SaleWithProductsEntity>> = saleDao.observeAll()

    suspend fun getByClientId(clientId: Int): List<SaleWithProductsEntity> {
        return saleDao.getByClientId(clientId)
    }

    suspend fun saveAll(sales: List<SaleEntity>) {
        saleDao.deleteAll()
        saleDao.insertAll(sales)
    }

    suspend fun getById(id: Int): SaleEntity? {
        return saleDao.getById(id)
    }
}
