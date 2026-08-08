package com.example.msp_app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.msp_app.core.database.dao.ClienteDao
import com.example.msp_app.core.database.dao.cobranzasync.CobranzaSyncStateDao
import com.example.msp_app.core.database.dao.guarantee.GuaranteeDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleComboDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleProductDao
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.product.ProductDao
import com.example.msp_app.core.database.dao.productInventory.ProductInventoryDao
import com.example.msp_app.core.database.dao.productInventoryImage.ProductInventoryImageDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.core.database.entities.GuaranteeEventEntity
import com.example.msp_app.core.database.entities.GuaranteeImageEntity
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleImageEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.database.entities.OverduePaymentsEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.ProductEntity
import com.example.msp_app.core.database.entities.ProductInventoryEntity
import com.example.msp_app.core.database.entities.ProductInventoryImageEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.migrations.MIGRATION_20_21
import com.example.msp_app.core.database.migrations.MIGRATION_21_22
import com.example.msp_app.core.database.migrations.MIGRATION_22_23
import com.example.msp_app.core.database.migrations.MIGRATION_23_24
import com.example.msp_app.core.database.migrations.MIGRATION_24_25
import com.example.msp_app.core.database.migrations.MIGRATION_25_26
import com.example.msp_app.core.database.migrations.MIGRATION_26_27

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
        ClienteEntity::class,
        CobranzaSyncStateEntity::class
    ],
    views = [OverduePaymentsEntity::class],
    version = 27,
    exportSchema = true
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
    abstract fun cobranzaSyncStateDao(): CobranzaSyncStateDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // `fallbackToDestructiveMigrationFrom` enumera versiones históricas de
        // schema (1..19, previas a que existiera migración incremental) —
        // son literales de un catálogo cerrado y ya congelado, no "cifras
        // mágicas" de negocio; nombrarlas una por una (`SCHEMA_VERSION_1`...)
        // sería puro ruido sin ganar legibilidad.
        @Suppress("MagicNumber")
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "msp_db"

                )
                    .addMigrations(
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23,
                        MIGRATION_23_24,
                        MIGRATION_24_25,
                        MIGRATION_25_26,
                        MIGRATION_26_27
                    )
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
                    .build().also { instance = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun setInstanceForTesting(database: AppDatabase) {
            instance = database
        }

        @androidx.annotation.VisibleForTesting
        fun clearInstance() {
            instance?.close()
            instance = null
        }
    }
}
