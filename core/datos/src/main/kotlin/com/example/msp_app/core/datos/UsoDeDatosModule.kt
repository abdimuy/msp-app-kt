package com.example.msp_app.core.datos

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** Expone el servicio del proceso (para el ViewModel de la pantalla). */
@Module
@InstallIn(SingletonComponent::class)
object UsoDeDatosModule {
    @Provides
    fun provideServicioDeUsoDeDatos(@ApplicationContext context: Context): ServicioDeUsoDeDatos =
        UsoDeDatos.servicio(context)
}
