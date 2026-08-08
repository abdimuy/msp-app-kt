package com.example.msp_app.di

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
 */
@Module
@InstallIn(SingletonComponent::class)
object ConnectivityModule {

    @Provides
    @Singleton
    fun provideConnectivityMonitor(@ApplicationContext context: Context): ConnectivityMonitor =
        ConnectivityMonitor.getInstance(context)
}
