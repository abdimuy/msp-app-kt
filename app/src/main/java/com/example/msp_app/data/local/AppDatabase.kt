package com.example.msp_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.msp_app.data.local.dao.ClienteDao
import com.example.msp_app.data.local.dao.guarantee.GuaranteeDao
import com.example.msp_app.data.local.dao.localsale.LocalSaleComboDao
import com.example.msp_app.data.local.dao.localsale.LocalSaleDao
import com.example.msp_app.data.local.dao.localsale.LocalSaleProductDao
import com.example.msp_app.data.local.dao.payment.PaymentDao
import com.example.msp_app.data.local.dao.product.ProductDao
import com.example.msp_app.data.local.dao.productInventory.ProductInventoryDao
import com.example.msp_app.data.local.dao.productInventoryImage.ProductInventoryImageDao
import com.example.msp_app.data.local.dao.sale.SaleDao
import com.example.msp_app.data.local.dao.visit.VisitDao
import com.example.msp_app.data.local.entities.ClienteEntity
import com.example.msp_app.data.local.entities.GuaranteeEntity
import com.example.msp_app.data.local.entities.GuaranteeEventEntity
import com.example.msp_app.data.local.entities.GuaranteeImageEntity
import com.example.msp_app.data.local.entities.LocalSaleComboEntity
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleImageEntity
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.local.entities.ProductEntity
import com.example.msp_app.data.local.entities.ProductInventoryEntity
import com.example.msp_app.data.local.entities.ProductInventoryImageEntity
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
        GuaranteeEventEntity::class,
        ProductInventoryEntity::class,
        ProductInventoryImageEntity::class,
        LocalSaleEntity::class,
        LocalSaleImageEntity::class,
        LocalSaleProductEntity::class,
        LocalSaleComboEntity::class,
        ClienteEntity::class
    ],
    version = 21,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun saleDao(): SaleDao
    abstract fun paymentDao(): PaymentDao
    abstract fun productDao(): ProductDao
    abstract fun visitDao(): VisitDao
    abstract fun guaranteeDao(): GuaranteeDao
    abstract fun productInventoryDao(): ProductInventoryDao
    abstract fun productInventoryImageDao(): ProductInventoryImageDao
    abstract fun localSaleDao(): LocalSaleDao
    abstract fun localSaleProduct(): LocalSaleProductDao
    abstract fun localSaleComboDao(): LocalSaleComboDao
    abstract fun clienteDao(): ClienteDao

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
