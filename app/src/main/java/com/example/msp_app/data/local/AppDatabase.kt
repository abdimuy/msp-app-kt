package com.example.msp_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.msp_app.data.local.dao.guarantee.GuaranteeDao
import com.example.msp_app.data.local.dao.payment.PaymentDao
import com.example.msp_app.data.local.dao.product.ProductDao
import com.example.msp_app.data.local.dao.sale.SaleDao
import com.example.msp_app.data.local.dao.visit.VisitDao
import com.example.msp_app.data.local.entities.GuaranteeEntity
import com.example.msp_app.data.local.entities.GuaranteeEventEntity
import com.example.msp_app.data.local.entities.GuaranteeImageEntity
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.local.entities.ProductEntity
import com.example.msp_app.data.local.entities.SaleEntity
import com.example.msp_app.data.local.entities.VisitEntity

@Database(
    entities = [
        SaleEntity::class,
        PaymentEntity::class,
        ProductEntity::class,
        VisitEntity::class,
        GuaranteeEntity::class,
        GuaranteeImageEntity::class,
        GuaranteeEventEntity::class
    ],
    version = 8,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun saleDao(): SaleDao
    abstract fun paymentDao(): PaymentDao
    abstract fun productDao(): ProductDao
    abstract fun visitDao(): VisitDao
    abstract fun guaranteeDao(): GuaranteeDao

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
