package com.example.msp_app.di

import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Expone por Hilt los servicios de red que hoy se obtienen vía los `object`
 * legacy (`ApiProvider`, `V2ApiProvider`, `ApiProviderImages`), SIN
 * reimplementar su Retrofit interno.
 *
 * Cada `@Provides` delega en `ApiProvider.create(...)` (mismo camino que las
 * ~27 llamadas legacy repartidas en ViewModels/repositorios/workers), así que
 * hereda intacto el rebuild de baseURL por Firestore (kill-switch remoto en
 * release) que vive en [ApiProvider]. Construir un `Retrofit` nuevo aquí
 * perdería ese listener — por eso NO se hace.
 *
 * YAGNI: solo se provee `WarehousesApi`, que es lo único que consume el
 * feature Warehouse (Task 8). Los demás servicios (`V2ApiProvider`,
 * `ApiProviderImages`, otros de `ApiProvider`) se agregan cuando su propio
 * feature migre a inyección — no se listan los 29 de golpe.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideWarehousesApi(): WarehousesApi = ApiProvider.create(WarehousesApi::class.java)
}
