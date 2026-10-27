package com.example.msp_app.di

import android.content.Context
import android.net.Uri
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.captura.CapturaEnCursoDao
import com.example.msp_app.data.captura.ReanudadorDeCapturas
import com.example.msp_app.data.captura.VentaLocalSinTerminarAdapter
import com.example.msp_app.feature.pagos.domain.port.ReanudarCapturasPort
import com.example.msp_app.feature.pagos.domain.port.VentaLocalSinTerminarPort
import com.example.msp_app.feature.visitas.domain.port.RegistroDeVisitaPort
import com.example.msp_app.features.guarantees.data.RegistroDeGarantia
import com.example.msp_app.features.guarantees.data.comprimirImagenesDeGarantia
import com.example.msp_app.workmanager.enqueuePendingGuaranteesWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * E-APP-119 (D15): la reanudación de visitas y garantías capturadas. SIN
 * `@Singleton`: lee la sesión vigente en cada llamada.
 */
@Module
@InstallIn(SingletonComponent::class)
object CapturaModule {

    @Provides
    fun provideReanudarCapturasPort(
        @ApplicationContext context: Context,
        db: AppDatabase,
        visitas: RegistroDeVisitaPort,
        clock: AppClock
    ): ReanudarCapturasPort = ReanudadorDeCapturas(
        visitas = visitas,
        garantias = RegistroDeGarantia(db, clock),
        comprimirGarantia = { fuentes, id ->
            comprimirImagenesDeGarantia(context, fuentes.map(Uri::parse), id)
        },
        encolarGarantia = { id -> enqueuePendingGuaranteesWorker(context, id) }
    )

    /** E-APP-119: la venta local sin terminar, destino del aviso. Lee la sesión vigente. */
    @Provides
    fun provideVentaLocalSinTerminarPort(capturas: CapturaEnCursoDao): VentaLocalSinTerminarPort =
        VentaLocalSinTerminarAdapter(capturas)
}
