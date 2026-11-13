package com.example.msp_app.feature.garantias.di

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.garantias.data.adapter.RoomColaDeComandosAdapter
import com.example.msp_app.feature.garantias.data.adapter.RoomEstadoDeLasFotosAdapter
import com.example.msp_app.feature.garantias.data.adapter.RoomFoliosSeguidosAdapter
import com.example.msp_app.feature.garantias.data.adapter.RoomFotosDeGarantiaAdapter
import com.example.msp_app.feature.garantias.data.adapter.RoomResolucionDeReferenciasAdapter
import com.example.msp_app.feature.garantias.data.queue.ProcesadorDeLaCola
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import com.example.msp_app.feature.garantias.domain.port.EstadoDeLasFotosPort
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import com.example.msp_app.feature.garantias.domain.port.FotosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.ProcesadorDeGarantias
import com.example.msp_app.feature.garantias.domain.port.ResolucionDeReferenciasPort
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/**
 * El reloj de `:feature:garantias`.
 *
 * Va cualificado porque `CollectionReportDataModule` ya provee un `AppClock`
 * sin cualificar en el mismo componente: dos bindings del mismo tipo sin
 * nombre serían un error de Hilt. El cualificador deja explícito que este
 * módulo no depende del reloj de otra pantalla.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GarantiasReloj

/** La única fuente de [AppClock] de este módulo; los tests inyectan el suyo. */
@Module
@InstallIn(SingletonComponent::class)
object GarantiasDataModule {

    @Provides
    @GarantiasReloj
    fun reloj(): AppClock = AppClock.System
}

/**
 * Une los puertos del módulo con sus adaptadores Room.
 *
 * Los adaptadores no llevan scope: son lectores/escritores de Room sin estado,
 * igual que los de `:feature:visitas`. El procesador tampoco: el trabajador lo
 * resuelve del grafo en cada corrida, para que un cambio de configuración no
 * quede congelado en una instancia vieja.
 *
 * **No se cablean aquí** `GarantiasRemotasPort` ni `ArchivosDeGarantiaPort`:
 * sus fuentes viven en `:app` (Retrofit y `filesDir`), así que sus adaptadores
 * y su `@Module` se van allí, con el precedente de `RegistroDeAbonoAdapter`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class GarantiasBindingsModule {

    @Binds
    abstract fun colaDeComandos(impl: RoomColaDeComandosAdapter): ColaDeComandosPort

    @Binds
    abstract fun resolucionDeReferencias(
        impl: RoomResolucionDeReferenciasAdapter
    ): ResolucionDeReferenciasPort

    @Binds
    abstract fun fotosDeGarantia(impl: RoomFotosDeGarantiaAdapter): FotosDeGarantiaPort

    @Binds
    abstract fun estadoDeLasFotos(impl: RoomEstadoDeLasFotosAdapter): EstadoDeLasFotosPort

    @Binds
    abstract fun foliosSeguidos(impl: RoomFoliosSeguidosAdapter): FoliosSeguidosPort

    @Binds
    abstract fun procesador(impl: ProcesadorDeLaCola): ProcesadorDeGarantias
}
