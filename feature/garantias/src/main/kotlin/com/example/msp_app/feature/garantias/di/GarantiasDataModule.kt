package com.example.msp_app.feature.garantias.di

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.garantiav2.GarantiaCatalogoDao
import com.example.msp_app.core.database.dao.garantiav2.GarantiaComandoDao
import com.example.msp_app.core.database.dao.garantiav2.GarantiaFolioDao
import com.example.msp_app.core.database.dao.garantiav2.GarantiaFotoDao
import com.example.msp_app.feature.garantias.data.adapter.RoomColaDeComandosAdapter
import com.example.msp_app.feature.garantias.data.adapter.RoomEstadoDeLasFotosAdapter
import com.example.msp_app.feature.garantias.data.adapter.RoomFoliosSeguidosAdapter
import com.example.msp_app.feature.garantias.data.adapter.RoomFotosDeGarantiaAdapter
import com.example.msp_app.feature.garantias.data.adapter.RoomResolucionDeReferenciasAdapter
import com.example.msp_app.feature.garantias.data.catalogo.CatalogoSinServidor
import com.example.msp_app.feature.garantias.data.claves.GeneradorDeClavesAdapter
import com.example.msp_app.feature.garantias.data.files.ArchivosDeGarantiaAdapter
import com.example.msp_app.feature.garantias.data.files.FirmaDeConformidadAdapter
import com.example.msp_app.feature.garantias.data.http.GarantiasRemotasAdapter
import com.example.msp_app.feature.garantias.data.queue.ProcesadorDeLaCola
import com.example.msp_app.feature.garantias.data.ubicacion.UbicacionDeGarantiaAdapter
import com.example.msp_app.feature.garantias.domain.port.ArchivosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.CatalogoDeGarantiasPort
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import com.example.msp_app.feature.garantias.domain.port.EstadoDeLasFotosPort
import com.example.msp_app.feature.garantias.domain.port.FirmaDeConformidadPort
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import com.example.msp_app.feature.garantias.domain.port.FotosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.GarantiasRemotasPort
import com.example.msp_app.feature.garantias.domain.port.GeneradorDeClavesPort
import com.example.msp_app.feature.garantias.domain.port.ProcesadorDeGarantias
import com.example.msp_app.feature.garantias.domain.port.ResolucionDeReferenciasPort
import com.example.msp_app.feature.garantias.domain.port.UbicacionDeGarantiaPort
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

    /**
     * Los DAOs de la captura, expuestos desde este módulo y no desde
     * `DatabaseModule`.
     *
     * `:core:database` ya expone [AppDatabase] con su `@Provides`, y el feature
     * depende de él. Declarar sus DAOs acá mantiene el alta del módulo en sus
     * propios archivos: `:core:database` sólo se toca para la migración y las
     * entidades, como pide D7. Son `@Provides` sin scope, igual que los del
     * `DatabaseModule`; Room memoiza sus proxies.
     */
    @Provides
    fun foliosSeguidosDao(db: AppDatabase): GarantiaFolioDao = db.garantiaFolioDao()

    @Provides
    fun comandosDao(db: AppDatabase): GarantiaComandoDao = db.garantiaComandoDao()

    @Provides
    fun fotosDao(db: AppDatabase): GarantiaFotoDao = db.garantiaFotoDao()

    @Provides
    fun catalogoDao(db: AppDatabase): GarantiaCatalogoDao = db.garantiaCatalogoDao()
}

/**
 * Une los puertos del módulo con sus adaptadores Room.
 *
 * Los adaptadores no llevan scope: son lectores/escritores de Room sin estado,
 * igual que los de `:feature:visitas`. El procesador tampoco: el trabajador lo
 * resuelve del grafo en cada corrida, para que un cambio de configuración no
 * quede congelado en una instancia vieja.
 *
 * **Todos** los adaptadores viven en el módulo. Los que necesitan Android
 * (`filesDir`, Play Services) reciben `Context` por Hilt; el de red recibe la
 * interfaz Retrofit, que `:app` construye con su baseURL y su token. `:app`
 * sólo cablea esas dos piezas externas en `GarantiasPortsModule`.
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

    @Binds
    abstract fun catalogo(impl: CatalogoSinServidor): CatalogoDeGarantiasPort

    @Binds
    abstract fun remotas(impl: GarantiasRemotasAdapter): GarantiasRemotasPort

    @Binds
    abstract fun archivos(impl: ArchivosDeGarantiaAdapter): ArchivosDeGarantiaPort

    @Binds
    abstract fun firma(impl: FirmaDeConformidadAdapter): FirmaDeConformidadPort

    @Binds
    abstract fun ubicacion(impl: UbicacionDeGarantiaAdapter): UbicacionDeGarantiaPort

    @Binds
    abstract fun generadorDeClaves(impl: GeneradorDeClavesAdapter): GeneradorDeClavesPort
}
