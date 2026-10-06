package com.example.msp_app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.dao.cobranzasync.CobranzaSyncStateDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.navegacion.domain.port.ContadoresDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.EstadoDeEnvioPort
import com.example.msp_app.core.navegacion.domain.port.PrivacidadDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.SeccionesPlegadasPort
import com.example.msp_app.core.navegacion.domain.port.TemaDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.ZonaDelUsuarioPort
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.menu.ContadoresDelMenuAdapter
import com.example.msp_app.data.menu.DataStoreSeccionesPlegadasAdapter
import com.example.msp_app.data.menu.EstadoDeEnvioAdapter
import com.example.msp_app.data.menu.SaleDaoZonaDelUsuarioAdapter
import com.example.msp_app.data.pagos.SettingsPrivacidadAdapter
import com.example.msp_app.data.pagos.ThemeControllerTemaDeLaAppAdapter
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.ui.CarteraEnPantalla
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** DataStore propio del menú lateral — calificado por la misma razón que `OrdenListaClientesDataStore`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MenuLateralDataStore

/**
 * Los puertos de `:core:navegacion` cuyas fuentes viven en `:app` (principio 17).
 * SIN `@Singleton` en los puertos: no sostienen estado propio; el DataStore sí
 * es `@Singleton` (DataStore revienta con dos instancias sobre el mismo archivo).
 *
 * Tema y Privacidad NO se cablean con un adaptador nuevo (ruling R2 del
 * controlador, 2026-10-06): se reusan los adaptadores reales de
 * `:feature:pagos` (`ThemeControllerTemaDeLaAppAdapter`,
 * `SettingsPrivacidadAdapter`), que ahora también implementan los puertos del
 * menú — ver sus KDoc para por qué alcanza con un `override` compartido.
 */
@Module
@InstallIn(SingletonComponent::class)
object MenuLateralModule {

    @Provides
    @Singleton
    @MenuLateralDataStore
    fun provideMenuLateralDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = {
            context.preferencesDataStoreFile("menu_lateral")
        })

    @Provides
    fun provideSeccionesPlegadasPort(
        @MenuLateralDataStore dataStore: DataStore<Preferences>
    ): SeccionesPlegadasPort = DataStoreSeccionesPlegadasAdapter(dataStore)

    @Provides
    fun provideEstadoDeEnvioPort(
        paymentDao: PaymentDao,
        localSaleDao: LocalSaleDao,
        syncStateDao: CobranzaSyncStateDao,
        clock: AppClock
    ): EstadoDeEnvioPort = EstadoDeEnvioAdapter(paymentDao, localSaleDao, syncStateDao, clock)

    /**
     * Ruling R6 (controlador, 2026-10-06): `ReunirCartera()` corre en
     * `withContext(io)` —el MISMO `@PagosIoDispatcher` que usa
     * `ListaDeClientesViewModel.kt:349`, no un dispatcher propio— porque son
     * tres lecturas completas de Room más agrupar/derivar, y `alAbrir` del
     * `MenuLateralViewModel` corre en `viewModelScope` (Main). Y con
     * `reportarIncidencias = false` (ver el KDoc de [ReunirCartera.invoke]):
     * abrir el menú no es una carga nueva de datos, así que no repite la
     * telemetría `CODE_VENTA_SIN_FECHA_LEGIBLE` que la lista ya reporta (o
     * reportará) por su cuenta.
     */
    @Provides
    fun provideContadoresDelMenuPort(
        reunirCartera: ReunirCartera,
        localSaleDao: LocalSaleDao,
        telemetry: Telemetry,
        @PagosIoDispatcher io: CoroutineDispatcher
    ): ContadoresDelMenuPort = ContadoresDelMenuAdapter(
        clientesPorVolver = {
            withContext(io) {
                val cartera = reunirCartera(reportarIncidencias = false)
                CarteraEnPantalla.clientesPorVolver(cartera.clientes, cartera.hoy)
            }
        },
        ventasSinSubir = { localSaleDao.observarSinSubir().first() },
        telemetry = telemetry
    )

    /** Ruling R3 (controlador, 2026-10-06) — ver el KDoc de [SaleDaoZonaDelUsuarioAdapter] para la fuente. */
    @Provides
    fun provideZonaDelUsuarioPort(saleDao: SaleDao): ZonaDelUsuarioPort =
        SaleDaoZonaDelUsuarioAdapter(saleDao)

    /**
     * Ruling R2: el mismo adaptador que `PagosPortsModule.provideTemaDeLaAppPort`,
     * una instancia nueva por binding (sin costo: no sostiene estado propio, ver
     * el KDoc de [ThemeControllerTemaDeLaAppAdapter]).
     */
    @Provides
    fun provideTemaDelMenuPort(): TemaDelMenuPort = ThemeControllerTemaDeLaAppAdapter()

    /**
     * Ruling R2: a diferencia del tema, aquí SÍ hace falta el MISMO singleton
     * que `PagosPortsModule.providePrivacidadPort` — Dagger lo garantiza porque
     * [SettingsPrivacidadAdapter] es `@Singleton`.
     */
    @Provides
    fun providePrivacidadDelMenuPort(adapter: SettingsPrivacidadAdapter): PrivacidadDelMenuPort =
        adapter
}
