package com.example.msp_app.data.local.dao.sale

import androidx.room.*
import com.example.msp_app.data.local.entities.SaleEntity

@Dao
interface SaleDao {

    @Query("SELECT * FROM sales")
    suspend fun getAll(): List<SaleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sales: List<SaleEntity>)

    @Query("DELETE FROM sales")
    suspend fun clearAll()
}
