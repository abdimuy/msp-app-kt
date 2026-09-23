package com.example.msp_app.di

import android.content.Context
import com.example.msp_app.data.ventacorreccion.WorkManagerReencolarSubidaAdapter
import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * Provee la implementación real de `ReencolarSubidaPort` (Task 3, plan "Corregir una venta
 * antes de que suba") — no se bindea en `:feature:ventaCorreccion` porque necesita
 * `PendingLocalSalesWorker`, que vive en `:app`. Mismo criterio que `CollectionReportUserCycleModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
object VentaCorreccionReencolarModule {

    @Provides
    fun provideReencolarSubidaPort(@ApplicationContext context: Context): ReencolarSubidaPort =
        WorkManagerReencolarSubidaAdapter(context)
}
