package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.SaleEntity

class SalesLocalDataSource(context: Context) {
    private val saleDao = AppDatabase.getInstance(context).saleDao()

    suspend fun getAll(): List<SaleEntity> = saleDao.getAll()

    suspend fun saveAll(sales: List<SaleEntity>) {
        saleDao.clearAll()
        saleDao.insertAll(sales)
    }

    suspend fun getById(id: Int): SaleEntity? {
        return saleDao.getAll().find { it.DOCTO_CC_ID == id }
    }
}