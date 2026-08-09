package com.example.msp_app.core.telemetry.di

import android.content.Context
import com.example.msp_app.core.telemetry.queue.TelemetryDatabase
import com.example.msp_app.core.telemetry.queue.TelemetryEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Expone por Hilt la [TelemetryDatabase] (`telemetry_db`, store PROPIO,
 * NUNCA `msp_db`/v27) y su DAO. Mismo patrón que
 * `com.example.msp_app.core.database.di.DatabaseModule`:
 * `provideTelemetryDatabase` DELEGA en [TelemetryDatabase.getInstance] y
 * NUNCA construye un `Room.databaseBuilder` propio acá — un builder nuevo
 * abriría una SEGUNDA conexión al mismo archivo (riesgo de locking) y
 * rompería el override que `TelemetryDatabase.setInstanceForTesting` ofrece
 * a los tests que inyectan una DB in-memory/de archivo por Hilt.
 *
 * La DB SÍ es `@Singleton` — a diferencia del `NetworkModule` (que evita
 * `@Singleton` sobre bindings que sostienen un `ApiProvider`/baseURL mutable
 * por el kill-switch de Firestore, ver `reference_msp_app_kt_hilt_baseurl_killswitch`),
 * esta DB no sostiene ningún estado mutable de red — la regla del
 * kill-switch NO aplica acá (ver brief de Task 3).
 *
 * El DAO deliberadamente SIN `@Singleton`: Room memoiza sus propios DAOs
 * internamente (proxies baratos sobre la misma conexión), así que no hay
 * costo en resolverlo de nuevo en cada inyección — mismo razonamiento que
 * `DatabaseModule` de `:core:database` usa para sus 12 DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object TelemetryDatabaseModule {

    @Provides
    @Singleton
    fun provideTelemetryDatabase(@ApplicationContext context: Context): TelemetryDatabase =
        TelemetryDatabase.getInstance(context)

    @Provides
    fun provideTelemetryEventDao(database: TelemetryDatabase): TelemetryEventDao =
        database.telemetryEventDao()
}
