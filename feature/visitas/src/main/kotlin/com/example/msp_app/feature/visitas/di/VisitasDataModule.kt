package com.example.msp_app.feature.visitas.di

import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitRecommendationDao
import com.example.msp_app.feature.visitas.data.adapter.RoomContextoDeVisitaAdapter
import com.example.msp_app.feature.visitas.data.adapter.RoomRecomendacionesAdapter
import com.example.msp_app.feature.visitas.domain.port.ContextoDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.RecomendacionesPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * `CoroutineDispatcher` de I/O de `:feature:visitas`. Cualificado para no
 * colisionar con ningún otro `CoroutineDispatcher` del grafo —empezando por el
 * de `:feature:pagos`— y para que los tests inyecten el suyo.
 *
 * No existe `DispatcherProvider` y no se crea (DISPATCH-CONVENTIONS).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VisitasIoDispatcher

/**
 * El ÚNICO archivo de `:feature:visitas` que ve las dos caras (puertos y
 * adaptadores) y las une.
 *
 * Adaptadores SIN `@Singleton`: son lectores Room baratos y sin estado (Room
 * memoiza sus proxies de DAO), mismo criterio que `PagosDataModule`; y la regla
 * de kill-switch prohíbe scopear cualquier cosa que sostenga sesión o red.
 *
 * **No se cablean aquí** `RegistroDeVisitaPort` ni `UbicacionPort`: sus fuentes
 * —la escritura que ya corre en producción y Play Services— viven en `:app`, así
 * que sus adaptadores y su `@Module` se van allá (precedente `RegistroDeAbonoPort`
 * → `RegistroDeAbonoAdapter`).
 */
@Module
@InstallIn(SingletonComponent::class)
object VisitasDataModule {

    @Provides
    fun provideContextoDeVisitaPort(saleDao: SaleDao): ContextoDeVisitaPort =
        RoomContextoDeVisitaAdapter(saleDao)

    @Provides
    fun provideRecomendacionesPort(dao: VisitRecommendationDao): RecomendacionesPort =
        RoomRecomendacionesAdapter(dao)

    @Provides
    @VisitasIoDispatcher
    fun provideVisitasIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
