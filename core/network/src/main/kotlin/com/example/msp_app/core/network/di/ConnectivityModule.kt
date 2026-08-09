package com.example.msp_app.core.network.di

import android.content.Context
import com.example.msp_app.core.network.ConnectivityMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Expone [ConnectivityMonitor] por Hilt delegando en su propio
 * `getInstance(context)` — respeta el singleton `@Volatile` que ya vive en el
 * companion object, no crea una segunda instancia paralela.
 *
 * Reubicado desde `:app` a `:core:network` (Task 5, Plan 4) junto con
 * [ConnectivityMonitor]: el módulo Hilt vive junto a lo que provee. `@Provides
 * @Singleton` se CONSERVA a propósito — [ConnectivityMonitor] no sostiene
 * ningún `ApiProvider.create()` (a diferencia de `NetworkModule.provideWarehousesApi`,
 * que sigue sin scope y sigue viviendo en `:app` por el kill-switch de
 * baseURL), así que memoizarlo por Hilt es seguro y correcto.
 */
@Module
@InstallIn(SingletonComponent::class)
object ConnectivityModule {

    @Provides
    @Singleton
    fun provideConnectivityMonitor(@ApplicationContext context: Context): ConnectivityMonitor =
        ConnectivityMonitor.getInstance(context)
}
