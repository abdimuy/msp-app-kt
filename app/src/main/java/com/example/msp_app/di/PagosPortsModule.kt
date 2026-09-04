package com.example.msp_app.di

import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.pagos.SettlementLiquidacionAdapter
import com.example.msp_app.data.pagos.UserCyclePeriodoDeCobroAdapter
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import com.example.msp_app.feature.pagos.domain.port.LiquidacionPort
import com.example.msp_app.feature.pagos.domain.port.PeriodoDeCobroPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Cablea los dos puertos de `:feature:pagos` cuyas fuentes viven en `:app`:
 * el cálculo de liquidación que ya existía y el `FECHA_CARGA_INICIAL` de
 * Firestore. Los puertos que leen Room se cablean dentro del feature
 * (`PagosDataModule`), igual que hace `:feature:collectionReport`.
 *
 * SIN `@Singleton` (kill-switch de sesión): [UserCyclePeriodoDeCobroAdapter]
 * consulta al usuario autenticado vigente en cada lectura.
 */
@Module
@InstallIn(SingletonComponent::class)
object PagosPortsModule {

    @Provides
    fun provideLiquidacionPort(saleDao: SaleDao, telemetry: Telemetry): LiquidacionPort =
        SettlementLiquidacionAdapter(saleDao, telemetry)

    @Provides
    fun providePeriodoDeCobroPort(userCyclePort: UserCyclePort): PeriodoDeCobroPort =
        UserCyclePeriodoDeCobroAdapter(userCyclePort)
}
