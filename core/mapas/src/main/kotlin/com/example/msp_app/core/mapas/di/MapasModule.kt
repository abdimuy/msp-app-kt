package com.example.msp_app.core.mapas.di

import android.content.Context
import com.example.msp_app.core.mapas.BuildConfig
import com.example.msp_app.core.mapas.adapters.AlmacenDelExtracto
import com.example.msp_app.core.mapas.adapters.DescargaDelExtracto
import com.example.msp_app.core.mapas.adapters.EstadoDelExtractoEnMemoria
import com.example.msp_app.core.mapas.adapters.ExtractoDeMapaAdapter
import com.example.msp_app.core.mapas.adapters.PlanificadorConWorkManager
import com.example.msp_app.core.mapas.adapters.PlanificadorDeLaDescarga
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.mapas.domain.port.ExtractoDeMapaPort
import com.example.msp_app.core.telemetry.Telemetry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import okhttp3.OkHttpClient

/**
 * `CoroutineDispatcher` de IO **de este módulo**, por `@Qualifier` de Hilt.
 *
 * No existe `DispatcherProvider` y no se crea (global-constraints §Dispatchers):
 * cuando el dispatcher cruza módulo se declara así, que es lo que ya hacen
 * `SpeechIoDispatcher` y `VisitasIoDispatcher`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapasIoDispatcher

/**
 * El `Call.Factory` **de este módulo**, calificado.
 *
 * Sin el calificador, Dagger encuentra tres `okhttp3.Call.Factory` en el grafo
 * de `:app` —el de `:core:appgate` (el APK), el de `:core:speech` (el modelo) y
 * éste— y falla con `DuplicateBindings`. Calificarlo, y no reusar aquéllos, es
 * lo correcto: son tres descargas con políticas distintas y compartir el cliente
 * ataría una a las otras.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapasCallFactory

/**
 * **El único archivo del módulo que ve las dos caras.**
 *
 * Lo que sale de acá hacia la app es un solo [ExtractoDeMapaPort] y el paquete
 * anunciado. Los adaptadores se construyen acá dentro.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object MapasModule {

    /**
     * El paquete del extracto, o **`null` si no hay de dónde bajarlo**.
     *
     * ## La URL no está escrita en el código, y no es un olvido
     *
     * El extracto se genera a mano con `go-pmtiles extract` sobre el build
     * diario de Protomaps (ver `huella-geografica-medida.md`) y **hoy no está
     * publicado en ningún servidor**. Inventar una URL para que la pantalla se
     * vea completa sería exactamente la clase de dato falso que este plan
     * persigue: un botón "Descargar" que no puede funcionar.
     *
     * Así que la URL entra por `MAPA_EXTRACTO_URL` de `local.properties` (o por
     * `-PMAPA_EXTRACTO_URL`), vacía por omisión. Vacía ⇒ `null` ⇒
     * `EstadoDelExtracto.SinOrigen`, y la pantalla lo dice.
     *
     * ## El tamaño SÍ está medido
     *
     * 25 507 515 bytes, el peso real de `ruta-cobranza-z14.pmtiles` el
     * 2026-09-16 (9 125 teselas, zoom 0–14, caja `-98.2,17.9,-96.4,19.6`). Es
     * orientativo a propósito: solo alimenta la barra de avance. Quien decide si
     * el archivo sirve es la cabecera, no este número.
     */
    @Provides
    @Singleton
    fun extracto(): ExtractoDeMapa? = BuildConfig.EXTRACTO_DE_MAPA_URL
        .takeIf { it.isNotBlank() }
        ?.let { ExtractoDeMapa(url = it, tamanoBytes = TAMANO_MEDIDO) }

    /** El peso medido del extracto. Ver el KDoc de [extracto]. */
    private const val TAMANO_MEDIDO = 25_507_515L

    @Provides
    @Singleton
    @MapasIoDispatcher
    fun dispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * Cliente propio y **no** el de `:core:network`: `NetworkKillSwitchGuardTest`
     * prohíbe que algo `@Singleton` sostenga un servicio de `ApiProvider.create()`
     * porque eso congelaría el `baseURL` de la flota. Acá no hay servicio ni
     * `baseURL` de la app — la URL del extracto es configuración de este módulo.
     */
    @Provides
    @Singleton
    @MapasCallFactory
    fun llamadas(): Call.Factory = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun almacen(@ApplicationContext context: Context, telemetry: Telemetry): AlmacenDelExtracto =
        AlmacenDelExtracto(File(context.filesDir, "mapas"), telemetry)

    @Provides
    @Singleton
    fun descarga(
        @MapasCallFactory llamadas: Call.Factory,
        almacen: AlmacenDelExtracto,
        telemetry: Telemetry,
        @MapasIoDispatcher dispatcher: CoroutineDispatcher
    ): DescargaDelExtracto = DescargaDelExtracto(llamadas, almacen, telemetry, dispatcher)

    @Provides
    @Singleton
    fun extractoPort(
        almacen: AlmacenDelExtracto,
        planificador: PlanificadorDeLaDescarga,
        estado: EstadoDelExtractoEnMemoria,
        extracto: ExtractoDeMapa?
    ): ExtractoDeMapaPort = ExtractoDeMapaAdapter(almacen, planificador, estado, extracto)
}

/** Las costuras con una sola implementación real. `@Binds` y nada más. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class MapasBindings {

    @Binds
    @Singleton
    abstract fun planificador(impl: PlanificadorConWorkManager): PlanificadorDeLaDescarga
}
