package com.example.msp_app.feature.ventacorreccion.di

import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.localsale.LocalSaleComboDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleProductDao
import com.example.msp_app.feature.ventacorreccion.data.AppClockRelojPort
import com.example.msp_app.feature.ventacorreccion.data.RoomVentaLocalCorreccionAdapter
import com.example.msp_app.feature.ventacorreccion.domain.port.RelojPort
import com.example.msp_app.feature.ventacorreccion.domain.port.VentaLocalCorreccionPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Cablea el puerto de persistencia de la corrección y el reloj de producción. Los DAOs
 * ([LocalSaleDao]/[LocalSaleProductDao]/[LocalSaleComboDao]) y [AppDatabase] ya los provee
 * `:core:database` (`DatabaseModule`).
 *
 * **No se bindea aquí [com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort]**
 * — mismo criterio que `UserCyclePort`/`ReportThemePort` en `:feature:collectionReport`
 * (`CollectionReportDataModule`, "No se bindea aquí"): su implementación real necesita
 * reencolar `PendingLocalSalesWorker`, que vive en `:app` y no es visible desde este módulo
 * (la dependencia entre módulos es unidireccional, `:app` → `:feature:ventaCorreccion`). Se
 * provee en `app/src/main/java/com/example/msp_app/di/VentaCorreccionReencolarModule.kt`.
 */
@Module
@InstallIn(SingletonComponent::class)
object VentaCorreccionModule {

    @Provides
    fun provideVentaLocalCorreccionPort(
        db: AppDatabase,
        localSaleDao: LocalSaleDao,
        localSaleProductDao: LocalSaleProductDao,
        localSaleComboDao: LocalSaleComboDao
    ): VentaLocalCorreccionPort =
        RoomVentaLocalCorreccionAdapter(db, localSaleDao, localSaleProductDao, localSaleComboDao)

    @Provides
    fun provideRelojPort(): RelojPort = AppClockRelojPort()
}
