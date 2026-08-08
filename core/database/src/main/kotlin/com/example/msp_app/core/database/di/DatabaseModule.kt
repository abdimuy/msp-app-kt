package com.example.msp_app.core.database.di

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Expone por Hilt la [AppDatabase] y sus 12 DAOs — el binding que las
 * Tasks 6-8 (datasources inyectados) y los futuros `@HiltViewModel`/
 * `@HiltWorker` consumen.
 *
 * Decisión del orquestador (strangler-fig, Plan 2 Task 3 — ver
 * `docs/superpowers/plans/2026-08-07-plan2-database.md`, NO reabrir):
 * `provideAppDatabase` DELEGA en [AppDatabase.getInstance] y NUNCA construye
 * un `Room.databaseBuilder` propio aquí. Un builder nuevo abriría una
 * SEGUNDA conexión al archivo `msp_db` (dos rutas de escritura al dinero →
 * riesgo de locking/corrupción) y `AppDatabase.setInstanceForTesting` dejaría
 * de alcanzar el grafo de Hilt — rompería el override en el que se apoya el
 * e2e de pagos existente. Delegar en `getInstance` mantiene exactamente UNA
 * conexión y preserva ese override intacto.
 *
 * `getInstance` NO se elimina en este plan: los ~7 callers legacy residuales
 * (ViewModels vía `viewModel()`, un worker aún no `@HiltWorker`, providers de
 * sesión/cobranza) quedan documentados como deuda rastreada, propiedad de sus
 * propios planes futuros por feature — no se fuerza su migración aquí.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    // Los DAOs deliberadamente SIN @Singleton: el singleton vive en la DB de
    // arriba y Room memoiza sus propios DAOs internamente (son proxies
    // baratos), así que no hay costo en resolverlos de nuevo en cada
    // inyección — mismo razonamiento de scoping explícito que ya usa
    // NetworkModule para WarehousesApi.
    @Provides
    fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()

    @Provides
    fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    fun provideVisitDao(db: AppDatabase): VisitDao = db.visitDao()

    @Provides
    fun provideGuaranteeDao(db: AppDatabase): GuaranteeDao = db.guaranteeDao()

    @Provides
    fun provideProductInventoryDao(db: AppDatabase): ProductInventoryDao = db.productInventoryDao()

    @Provides
    fun provideProductInventoryImageDao(db: AppDatabase): ProductInventoryImageDao =
        db.productInventoryImageDao()

    @Provides
    fun provideLocalSaleDao(db: AppDatabase): LocalSaleDao = db.localSaleDao()

    // Nombre exacto del método en AppDatabase: `localSaleProduct()`, NO
    // `localSaleProductDao()` — gotcha señalado en el brief de Task 3.
    @Provides
    fun provideLocalSaleProductDao(db: AppDatabase): LocalSaleProductDao = db.localSaleProduct()

    @Provides
    fun provideLocalSaleComboDao(db: AppDatabase): LocalSaleComboDao = db.localSaleComboDao()

    @Provides
    fun provideClienteDao(db: AppDatabase): ClienteDao = db.clienteDao()

    @Provides
    fun provideCobranzaSyncStateDao(db: AppDatabase): CobranzaSyncStateDao =
        db.cobranzaSyncStateDao()
}
