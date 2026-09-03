package com.example.msp_app.di

import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.visits.V2VisitsApi
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

    /**
     * Servicio v2 de visitas — hoy solo lo inyecta el reconciliador de visitas
     * (Task 10, `V2VisitCustodyRegistry`); `PendingVisitsWorker` sigue
     * construyéndolo directo por `V2ApiProvider.create(...)` como el resto del
     * código legacy, sin cambios.
     *
     * Igual que [provideWarehousesApi], deliberadamente **SIN `@Singleton`**.
     * La baseURL v2 no está hoy bajo el kill-switch de Firestore (es estática
     * por flavor, ver KDoc de [V2ApiProvider]), así que el riesgo concreto de
     * congelarla no existe *todavía* — pero la regla del repo es sobre la
     * FORMA, no sobre qué proveedor está bajo kill-switch este mes: nada
     * `@Singleton` sostiene un servicio salido de un `ApiProvider`. Poner
     * scope acá sería el precedente que alguien copia el día que la v2 sí
     * gane un override remoto, y entonces el flip no alcanzaría a nadie.
     * `NetworkKillSwitchGuardTest` fija esta forma por reflexión.
     */
    @Provides
    fun provideV2VisitsApi(): V2VisitsApi = V2ApiProvider.create(V2VisitsApi::class.java)
}
