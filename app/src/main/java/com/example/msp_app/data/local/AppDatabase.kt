package com.example.msp_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.msp_app.data.local.dao.payment.PaymentDao
import com.example.msp_app.data.local.dao.product.ProductDao
import com.example.msp_app.data.local.dao.productInventory.ProductInventoryDao
import com.example.msp_app.data.local.dao.sale.SaleDao
import com.example.msp_app.data.local.dao.visit.VisitDao
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.local.entities.ProductEntity
import com.example.msp_app.data.local.entities.ProductInventoryEntity
import com.example.msp_app.data.local.entities.SaleEntity
import com.example.msp_app.data.local.entities.VisitEntity

@Database(
    entities = [
        SaleEntity::class,
        PaymentEntity::class,
        ProductEntity::class,
        VisitEntity::class,
        ProductInventoryEntity::class
    ],
    version = 7,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun saleDao(): SaleDao
    abstract fun paymentDao(): PaymentDao
    abstract fun productDao(): ProductDao
    abstract fun visitDao(): VisitDao
    abstract fun productInventoryDao(): ProductInventoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "msp_db"

                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
