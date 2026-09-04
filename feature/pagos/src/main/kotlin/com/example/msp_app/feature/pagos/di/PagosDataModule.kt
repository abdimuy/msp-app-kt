package com.example.msp_app.feature.pagos.di

import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.data.adapter.RoomPagosAdapter
import com.example.msp_app.feature.pagos.data.adapter.RoomVentasAdapter
import com.example.msp_app.feature.pagos.data.adapter.RoomVisitasAdapter
import com.example.msp_app.feature.pagos.domain.port.PagosPort
import com.example.msp_app.feature.pagos.domain.port.VentasPort
import com.example.msp_app.feature.pagos.domain.port.VisitasPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * `CoroutineDispatcher` de I/O de `:feature:pagos` — la carga del detalle son
 * varias lecturas Room seguidas. Cualificado para no colisionar con ningún otro
 * `CoroutineDispatcher` del grafo y para que los tests inyecten el suyo.
 *
 * No existe `DispatcherProvider` y no se crea (DISPATCH-CONVENTIONS).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PagosIoDispatcher

/**
 * El ÚNICO archivo de `:feature:pagos` que ve las dos caras (puertos y
 * adaptadores) y las une.
 *
 * Adaptadores SIN `@Singleton`: son lectores Room baratos y sin estado (Room
 * memoiza sus proxies de DAO), mismo criterio que `CollectionReportDataModule`;
 * y la regla de kill-switch prohíbe scopear cualquier cosa que sostenga
 * sesión/red.
 *
 * **No se cablean aquí** [com.example.msp_app.feature.pagos.domain.port.LiquidacionPort]
 * ni [com.example.msp_app.feature.pagos.domain.port.PeriodoDeCobroPort]: sus
 * fuentes (el cálculo de liquidación ya existente y el `userData` de Firestore)
 * viven en `:app`, así que sus adaptadores y su `@Module` se van allá —
 * precedente `UserCyclePort` → `FirebaseUserCycleAdapter`.
 *
 * **`AppClock` tampoco se provee aquí**: `CollectionReportDataModule` ya lo
 * bindea en el mismo `SingletonComponent` y un segundo `@Provides` del mismo
 * tipo sin cualificador es una duplicate binding que no compila. Se consume el
 * que ya existe — hay un solo reloj en el grafo, que es justo la invariante que
 * `AppTime`/`AppClock` vinieron a imponer.
 */
@Module
@InstallIn(SingletonComponent::class)
object PagosDataModule {

    @Provides
    fun provideVentasPort(saleDao: SaleDao): VentasPort = RoomVentasAdapter(saleDao)

    @Provides
    fun providePagosPort(paymentDao: PaymentDao): PagosPort = RoomPagosAdapter(paymentDao)

    @Provides
    fun provideVisitasPort(visitDao: VisitDao, telemetry: Telemetry): VisitasPort =
        RoomVisitasAdapter(visitDao, telemetry)

    @Provides
    @PagosIoDispatcher
    fun providePagosIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
