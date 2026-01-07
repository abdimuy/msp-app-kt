package com.example.msp_app.data.local.dao.localsale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.LocalSaleComboEntity

@Dao
interface LocalSaleComboDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCombo(combo: LocalSaleComboEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCombos(combos: List<LocalSaleComboEntity>)

    @Query("SELECT * FROM local_sale_combos WHERE LOCAL_SALE_ID = :saleId")
    suspend fun getCombosForSale(saleId: String): List<LocalSaleComboEntity>

    @Query("DELETE FROM local_sale_combos WHERE LOCAL_SALE_ID = :saleId")
    suspend fun deleteCombosForSale(saleId: String)
}
