package com.example.msp_app.di

import android.content.Context
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.dao.visit.VisitRecommendationDao
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.VisitsWorkManagerEnqueuer
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.visitas.ComprobantesDeVisitaAdapter
import com.example.msp_app.data.visitas.RegistroDeVisitaAdapter
import com.example.msp_app.data.visitas.ThemeControllerTemaDeLaAppAdapter
import com.example.msp_app.data.visitas.UbicacionDeVisitaAdapter
import com.example.msp_app.feature.visitas.domain.port.ComprobantesDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.CuentaAusentePort
import com.example.msp_app.feature.visitas.domain.port.FaseDelGuardado
import com.example.msp_app.feature.visitas.domain.port.GuardadoCanceladoPort
import com.example.msp_app.feature.visitas.domain.port.RegistroDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.TemaDeLaAppPort
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * Cablea los puertos de `:feature:visitas` cuyas fuentes viven en `:app`: la
 * escritura que ya corre en producción, la ubicación de Play Services, la
 * cámara del comprobante (Task 23 — `FileProvider`/`ImageCompressor`) y el tema
 * global de la app. Los puertos que solo leen Room se cablean dentro del feature
 * (`VisitasDataModule`), igual que hace `:feature:pagos`.
 *
 * SIN `@Singleton` (kill-switch de sesión): el adaptador de registro resuelve al
 * usuario autenticado vigente en cada escritura, así que sostiene sesión.
 */
@Module
@InstallIn(SingletonComponent::class)
object VisitasPortsModule {

    /**
     * El tema GLOBAL de la app para las pantallas de visita. No lo pide un botón
     * sol/luna —ninguna de las dos lo pinta todavía— sino
     * `MspThemeRevealHost`, que necesita poder pedir el flip DESPUÉS de haber
     * grabado el frame viejo. Vive aquí y no en `VisitasDataModule` por la misma
     * razón que sus vecinos: `ThemeController` es de `:app`, fuera del alcance
     * del módulo de feature.
     *
     * SIN `@Singleton`, igual que [PagosPortsModule], [CollectionReportThemeModule]
     * y [ConfiguracionThemeModule]: [ThemeControllerTemaDeLaAppAdapter] no
     * sostiene ningún estado propio (delega TODO en el objeto `ThemeController`,
     * que ya es el singleton real), así que instanciarlo por inyección es tan
     * caro como no hacerlo.
     */
    @Provides
    fun provideTemaDeLaAppPort(): TemaDeLaAppPort = ThemeControllerTemaDeLaAppAdapter()

    /**
     * El encolador se construye aquí y no se inyecta: `VisitsWorkEnqueuer` no
     * tiene binding en el grafo — hoy `VisitsLocalDataSource` lo recibe por el
     * puente `context` que usan los ViewModels no-Hilt. Se usa la MISMA
     * implementación de producción ([VisitsWorkManagerEnqueuer]), así que el
     * encolado del envío es exactamente el que corre hoy (Task 5).
     */
    @Provides
    @Suppress("LongParameterList") // un @Provides con las dependencias reales del adaptador.
    fun provideRegistroDeVisitaPort(
        @ApplicationContext context: Context,
        db: AppDatabase,
        saleDao: SaleDao,
        visitDao: VisitDao,
        recomendaciones: VisitRecommendationDao,
        imagenes: VisitImageDao,
        telemetry: Telemetry,
        clock: AppClock
    ): RegistroDeVisitaPort = RegistroDeVisitaAdapter(
        db = db,
        saleDao = saleDao,
        visitas = VisitsLocalDataSource(
            visitDao = visitDao,
            saleDao = saleDao,
            enqueuer = VisitsWorkManagerEnqueuer(context),
            clock = clock
        ),
        recomendaciones = recomendaciones,
        imagenes = imagenes,
        telemetry = telemetry,
        clock = clock
    )

    @Provides
    fun provideUbicacionPort(@ApplicationContext context: Context): UbicacionPort =
        UbicacionDeVisitaAdapter(context)

    /**
     * La cámara del comprobante. SIN `@Singleton`, como los otros dos: no
     * sostiene nada que deba sobrevivir a un cambio de sesión.
     */
    @Provides
    fun provideComprobantesDeVisitaPort(
        @ApplicationContext context: Context,
        imagenes: VisitImageDao,
        telemetry: Telemetry,
        clock: AppClock
    ): ComprobantesDeVisitaPort = ComprobantesDeVisitaAdapter(
        context = context,
        imagenes = imagenes,
        telemetry = telemetry,
        clock = clock
    )

    /**
     * E-APP-078: el aviso de un guardado de visita cancelado sale por
     * [TelemetriaDeVisitas] (`RemoteLogger`, lo único que llega a la oficina);
     * el puerto `Telemetry` termina en `StubTelemetrySink` y no sale del
     * teléfono. Solo conteos, filtrados por la lista blanca cerrada.
     */
    @Provides
    fun provideGuardadoCanceladoPort(): GuardadoCanceladoPort =
        GuardadoCanceladoPort { fase, escritas, cuentas ->
            TelemetriaDeVisitas.emitir(
                TelemetriaDeVisitas.EVENTO_GUARDADO_CANCELADO,
                TelemetriaDeVisitas.guardadoCancelado(
                    escritas = escritas,
                    cuentas = cuentas,
                    enUbicacion = fase == FaseDelGuardado.UBICACION
                )
            )
        }

    /**
     * H-r4-5: el reintento congelado que escribe cuentas ausentes sale por
     * [TelemetriaDeVisitas]. Solo el conteo, filtrado por la lista blanca.
     */
    @Provides
    fun provideCuentaAusentePort(): CuentaAusentePort = CuentaAusentePort { cuantas ->
        TelemetriaDeVisitas.emitir(
            TelemetriaDeVisitas.EVENTO_CUENTA_AUSENTE,
            TelemetriaDeVisitas.cuentaAusente(cuantas)
        )
    }
}
