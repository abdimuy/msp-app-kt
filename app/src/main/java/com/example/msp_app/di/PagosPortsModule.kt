package com.example.msp_app.di

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.pagos.RegistroDeAbonoAdapter
import com.example.msp_app.data.pagos.SettlementLiquidacionAdapter
import com.example.msp_app.data.pagos.UserCyclePeriodoDeCobroAdapter
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import com.example.msp_app.feature.pagos.domain.port.LiquidacionPort
import com.example.msp_app.feature.pagos.domain.port.PeriodoDeCobroPort
import com.example.msp_app.feature.pagos.domain.port.RegistroDeAbonoPort
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

    /**
     * La escritura del abono (Task 18). SIN `@Singleton`: resuelve el usuario
     * autenticado vigente en cada registro, así que sostiene sesión.
     */
    @Provides
    fun provideRegistroDeAbonoPort(
        db: AppDatabase,
        saleDao: SaleDao,
        paymentDao: PaymentDao,
        telemetry: Telemetry,
        clock: AppClock
    ): RegistroDeAbonoPort = RegistroDeAbonoAdapter(
        db = db,
        saleDao = saleDao,
        pagos = PaymentsLocalDataSource(paymentDao, saleDao),
        telemetry = telemetry,
        clock = clock
    )
}
