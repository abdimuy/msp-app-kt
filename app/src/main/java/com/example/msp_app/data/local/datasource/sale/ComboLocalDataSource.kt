package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.LocalSaleComboEntity

class ComboLocalDataSource(context: Context) {
    private val comboDao = AppDatabase.getInstance(context).localSaleComboDao()

    suspend fun insertCombo(combo: LocalSaleComboEntity) {
        comboDao.insertCombo(combo)
    }

    suspend fun insertCombos(combos: List<LocalSaleComboEntity>) {
        comboDao.insertAllCombos(combos)
    }

    suspend fun getCombosForSale(saleId: String): List<LocalSaleComboEntity> {
        return try {
            comboDao.getCombosForSale(saleId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteCombosForSale(saleId: String) {
        comboDao.deleteCombosForSale(saleId)
    }
}
