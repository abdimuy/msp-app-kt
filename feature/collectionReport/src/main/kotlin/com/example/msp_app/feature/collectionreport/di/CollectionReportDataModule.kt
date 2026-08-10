package com.example.msp_app.feature.collectionreport.di

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.feature.collectionreport.data.adapter.RoomHistoricalTotalsAdapter
import com.example.msp_app.feature.collectionreport.data.adapter.RoomPaymentsAdapter
import com.example.msp_app.feature.collectionreport.data.adapter.RoomVisitsAdapter
import com.example.msp_app.feature.collectionreport.domain.port.HistoricalTotalsPort
import com.example.msp_app.feature.collectionreport.domain.port.PaymentsPort
import com.example.msp_app.feature.collectionreport.domain.port.VisitsPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Marca el `CoroutineDispatcher` de CPU (`Dispatchers.Default`) que el reporte usa para sacar
 * de Main la agregación pesada de la carga (BigDecimal/timeline/sort en
 * `CollectionReportStateBuilder.buildContent`). Cualificado para no colisionar con ningún otro
 * `CoroutineDispatcher` del grafo y para que los tests puedan inyectar un dispatcher de prueba.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Cablea los puertos de datos del reporte a sus adapters Room. Los DAOs
 * ([PaymentDao] / [VisitDao]) los provee `:core:database` (`DatabaseModule`).
 *
 * Adapters SIN `@Singleton` a propósito: son lectores Room baratos y sin estado
 * (Room memoiza sus proxies de DAO), mismo criterio que `DatabaseModule` para
 * los DAOs; y la regla de kill-switch prohíbe scopear cualquier cosa que
 * sostenga sesión/red.
 *
 * **No se bindea aquí:**
 * - [com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort]:
 *   su fuente (`userData` de Firestore) vive en `:app`; su implementación se
 *   provee en el composition root de `:app` (precedente `AuthTokenProvider`).
 * - [com.example.msp_app.feature.collectionreport.domain.port.TransfersPort]:
 *   parked (el mockup no lo pide) — sin adapter ni binding hasta que exista un
 *   consumidor real.
 */
@Module
@InstallIn(SingletonComponent::class)
object CollectionReportDataModule {

    @Provides
    fun providePaymentsPort(paymentDao: PaymentDao): PaymentsPort = RoomPaymentsAdapter(paymentDao)

    @Provides
    fun provideVisitsPort(visitDao: VisitDao): VisitsPort = RoomVisitsAdapter(visitDao)

    @Provides
    fun provideHistoricalTotalsPort(paymentDao: PaymentDao): HistoricalTotalsPort =
        RoomHistoricalTotalsAdapter(paymentDao, AppClock.System)

    /**
     * Reloj de producción para [com.example.msp_app.feature.collectionreport.ui.CollectionReportViewModel]
     * (cálculo de rangos día/ciclo). `AppClock.System` es el único reloj real; los tests inyectan un
     * FakeClock construyendo el ViewModel a mano. Sin `@Singleton`: `AppClock.System` ya es un objeto
     * único e inmutable, y no sostiene sesión/red. Nadie más en el grafo provee `AppClock` hoy, así
     * que esta única fuente no colisiona.
     */
    @Provides
    fun provideAppClock(): AppClock = AppClock.System

    /**
     * `Dispatchers.Default` (CPU) para la carga del reporte. Los tests inyectan un dispatcher de
     * prueba directo al ViewModel, así que este binding solo alimenta producción.
     */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
