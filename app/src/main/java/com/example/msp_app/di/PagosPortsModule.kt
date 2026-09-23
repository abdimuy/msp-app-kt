package com.example.msp_app.di

import android.content.Context
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PaymentsWorkEnqueuer
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.clientprofile.ClientProfileDao
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.payment.PaymentImageDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.PaymentsWorkManagerEnqueuer
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.pagos.ComprobantesDeAbonoAdapter
import com.example.msp_app.data.pagos.FichaDelClienteAdapter
import com.example.msp_app.data.pagos.RegistroDeAbonoAdapter
import com.example.msp_app.data.pagos.SettingsPrivacidadAdapter
import com.example.msp_app.data.pagos.SettlementLiquidacionAdapter
import com.example.msp_app.data.pagos.ThemeControllerTemaDeLaAppAdapter
import com.example.msp_app.data.pagos.UserCyclePeriodoDeCobroAdapter
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import com.example.msp_app.feature.pagos.domain.port.ComprobantesPort
import com.example.msp_app.feature.pagos.domain.port.FichaDelClientePort
import com.example.msp_app.feature.pagos.domain.port.LiquidacionPort
import com.example.msp_app.feature.pagos.domain.port.PeriodoDeCobroPort
import com.example.msp_app.feature.pagos.domain.port.PrivacidadPort
import com.example.msp_app.feature.pagos.domain.port.RegistroDeAbonoPort
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import com.example.msp_app.services.pedirUbicacionDelPago
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * Cablea los puertos de `:feature:pagos` cuyas fuentes viven en `:app`: el
 * cálculo de liquidación que ya existía, el `FECHA_CARGA_INICIAL` de Firestore,
 * la escritura del abono y la cámara del comprobante (Task 22 — necesita
 * `FileProvider` e `ImageCompressor`, que viven aquí). Los puertos que leen
 * Room se cablean dentro del feature
 * (`PagosDataModule`), igual que hace `:feature:collectionReport`.
 *
 * SIN `@Singleton` (kill-switch de sesión): [UserCyclePeriodoDeCobroAdapter]
 * consulta al usuario autenticado vigente en cada lectura.
 */
@Module
@InstallIn(SingletonComponent::class)
object PagosPortsModule {

    /**
     * El tema GLOBAL de la app para el encabezado de la lista de clientes — el
     * botón sol/luna que el dueño no encontraba. Vive aquí y no en
     * `PagosDataModule` por la misma razón que sus vecinos: `ThemeController`
     * es de `:app`, fuera del alcance del módulo de feature.
     *
     * SIN `@Singleton`, igual que [CollectionReportThemeModule] y
     * [ConfiguracionThemeModule]: [ThemeControllerTemaDeLaAppAdapter] no
     * sostiene ningún estado propio (delega TODO en el objeto `ThemeController`,
     * que ya es el singleton real), así que instanciarlo por inyección es tan
     * caro como no hacerlo.
     */
    @Provides
    fun provideTemaDeLaAppPort(): TemaDeLaAppPort = ThemeControllerTemaDeLaAppAdapter()

    @Provides
    fun provideLiquidacionPort(saleDao: SaleDao, telemetry: Telemetry): LiquidacionPort =
        SettlementLiquidacionAdapter(saleDao, telemetry)

    @Provides
    fun providePeriodoDeCobroPort(userCyclePort: UserCyclePort): PeriodoDeCobroPort =
        UserCyclePeriodoDeCobroAdapter(userCyclePort)

    /**
     * "Esconder cantidades". El adaptador es `@Singleton` a propósito: cachea la
     * preferencia en un `StateFlow` para poder contestar `ocultosAhora()` sin
     * suspender, y un adaptador nuevo por inyección tiraría esa caché.
     */
    @Provides
    fun providePrivacidadPort(adapter: SettingsPrivacidadAdapter): PrivacidadPort = adapter

    /**
     * La escritura del abono (Task 18). SIN `@Singleton`: resuelve el usuario
     * autenticado vigente en cada registro, así que sostiene sesión.
     *
     * `pedirUbicacion` es el ÚNICO lugar donde el camino nuevo del abono toca
     * `android.content.Intent`: el adaptador recibe una lambda y por eso se
     * prueba con un fake, sin arrancar un servicio real.
     *
     * El encolador se construye aquí y no se inyecta, mismo reparto que
     * `VisitasPortsModule`: [PaymentsWorkEnqueuer] no tiene binding de Hilt y su
     * única implementación necesita el `Context` de aplicación.
     */
    @Provides
    fun provideRegistroDeAbonoPort(
        @ApplicationContext context: Context,
        db: AppDatabase,
        saleDao: SaleDao,
        paymentDao: PaymentDao,
        paymentImageDao: PaymentImageDao,
        telemetry: Telemetry,
        clock: AppClock
    ): RegistroDeAbonoPort = RegistroDeAbonoAdapter(
        db = db,
        saleDao = saleDao,
        pagos = PaymentsLocalDataSource(paymentDao, saleDao),
        imagenes = paymentImageDao,
        telemetry = telemetry,
        encolador = PaymentsWorkManagerEnqueuer(context),
        clock = clock,
        pedirUbicacion = { pagoId -> pedirUbicacionDelPago(context, pagoId) }
    )

    /**
     * La cámara del comprobante (Task 22). SIN `@Singleton`, igual que sus
     * vecinos: no sostiene ni sesión ni red, y el `Context` de aplicación ya es
     * único de por sí.
     */
    @Provides
    fun provideComprobantesPort(
        @ApplicationContext context: Context,
        paymentImageDao: PaymentImageDao,
        telemetry: Telemetry,
        clock: AppClock
    ): ComprobantesPort = ComprobantesDeAbonoAdapter(
        context = context,
        imagenes = paymentImageDao,
        telemetry = telemetry,
        clock = clock
    )

    /**
     * La ficha del cliente (Task 24). Vive aquí y no en `PagosDataModule`
     * porque su `COBRADOR_ID` sale del usuario autenticado de Firestore, que
     * solo existe en `:app`.
     *
     * SIN `@Singleton` (kill-switch de sesión): resuelve el usuario vigente en
     * cada guardado, igual que [RegistroDeAbonoAdapter].
     */
    @Provides
    fun provideFichaDelClientePort(
        clientProfileDao: ClientProfileDao,
        telemetry: Telemetry,
        clock: AppClock
    ): FichaDelClientePort = FichaDelClienteAdapter(
        fichas = clientProfileDao,
        telemetry = telemetry,
        clock = clock
    )
}
