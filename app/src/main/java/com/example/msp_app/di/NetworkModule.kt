package com.example.msp_app.di

import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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

    /**
     * Deliberadamente SIN `@Singleton`: `WarehousesApi` es una interfaz cuya
     * única fuente de verdad de baseURL es el `Retrofit` interno y mutable de
     * [ApiProvider], reescrito en runtime por el listener de Firestore
     * (kill-switch remoto en release). Si esta función fuera `@Singleton`,
     * Hilt memoizaría el proxy devuelto por `ApiProvider.create(...)` para
     * TODA la vida del proceso — el kill-switch dejaría de alcanzar a
     * cualquier consumidor inyectado tras el primer flip de baseURL.
     *
     * Sin scope, cada punto de inyección obtiene su propia llamada a
     * `create(...)` (Hilt no cachea el resultado), calcada del patrón legacy
     * dominante: un `val` congelado en el constructor de un ViewModel/Worker
     * que se refresca cuando ese ViewModel/Worker se recrea — ni mejor ni
     * peor que hoy, solo el mismo trade-off ya aceptado en el resto del
     * código. Un consumidor que además necesite reactividad DENTRO de su
     * propio ciclo de vida (sin esperar a una recreación) puede en su lugar
     * inyectar `Provider<WarehousesApi>` y llamar `.get()` en cada uso, o
     * inyectar [ApiProvider] mismo y llamar `create(...)` directamente.
     */
    @Provides
    fun provideWarehousesApi(): WarehousesApi = ApiProvider.create(WarehousesApi::class.java)
}
