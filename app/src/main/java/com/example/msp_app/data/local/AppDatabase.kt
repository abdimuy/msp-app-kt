package com.example.msp_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.msp_app.data.local.dao.sale.SaleDao
import com.example.msp_app.data.local.entities.SaleEntity

@Database(entities = [SaleEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun saleDao(): SaleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "msp_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
