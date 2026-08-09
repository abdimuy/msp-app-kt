package com.example.msp_app.core.telemetry.di

import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.telemetry.adapter.DurableTelemetry
import com.example.msp_app.core.telemetry.adapter.StubTelemetrySink
import com.example.msp_app.core.telemetry.queue.DurableTelemetryQueue
import com.example.msp_app.core.telemetry.queue.TelemetryEventDao
import com.example.msp_app.core.telemetry.queue.TelemetrySink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Cablea el puerto [Telemetry] al adapter real [DurableTelemetry] (Plan 4,
 * Task 4), drenando hacia el [StubTelemetrySink] (sin red todavía — ver KDoc
 * de esa clase). `:app` inyecta [Telemetry] sin conocer ninguno de los dos.
 *
 * Los 3 bindings son `@Singleton`: a diferencia de `NetworkModule` (que evita
 * `@Singleton` sobre lo que sostiene un `ApiProvider`/baseURL mutable por el
 * kill-switch de Firestore, ver `reference_msp_app_kt_hilt_baseurl_killswitch`),
 * NADA acá sostiene un cliente de red — [DurableTelemetryQueue] envuelve un
 * DAO de Room, y el sink stub no hace red real — la regla del kill-switch NO
 * aplica (mismo razonamiento que ya usa `TelemetryDatabaseModule`, Task 3).
 */
@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {

    @Provides
    @Singleton
    fun provideDurableTelemetryQueue(dao: TelemetryEventDao): DurableTelemetryQueue =
        DurableTelemetryQueue(dao)

    @Provides
    @Singleton
    fun provideTelemetrySink(): TelemetrySink = StubTelemetrySink()

    @Provides
    @Singleton
    fun provideTelemetry(queue: DurableTelemetryQueue, sink: TelemetrySink): Telemetry =
        DurableTelemetry(queue, sink)
}
