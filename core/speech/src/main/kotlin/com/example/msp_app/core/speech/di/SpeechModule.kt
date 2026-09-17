package com.example.msp_app.core.speech.di

import android.content.Context
import com.example.msp_app.core.speech.adapters.AlmacenDelModelo
import com.example.msp_app.core.speech.adapters.AndroidDictadoAdapter
import com.example.msp_app.core.speech.adapters.AudioRecordGrabadora
import com.example.msp_app.core.speech.adapters.DescargaDelModelo
import com.example.msp_app.core.speech.adapters.DictadoSegunElMotor
import com.example.msp_app.core.speech.adapters.EstadoDelModeloEnMemoria
import com.example.msp_app.core.speech.adapters.GrabadoraDeAudio
import com.example.msp_app.core.speech.adapters.ModeloDeDictadoAdapter
import com.example.msp_app.core.speech.adapters.MotorWhisperNativo
import com.example.msp_app.core.speech.adapters.PermisoDeMicrofono
import com.example.msp_app.core.speech.adapters.PermisoDeMicrofonoDelSistema
import com.example.msp_app.core.speech.adapters.PlanificadorConWorkManager
import com.example.msp_app.core.speech.adapters.PlanificadorDeLaDescarga
import com.example.msp_app.core.speech.adapters.ReconocedorDeAndroid
import com.example.msp_app.core.speech.adapters.SpeechRecognizerDeAndroid
import com.example.msp_app.core.speech.adapters.WhisperDictadoAdapter
import com.example.msp_app.core.speech.adapters.WhisperJni
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.speech.domain.port.DictadoPort
import com.example.msp_app.core.speech.domain.port.ModeloDeDictadoPort
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
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import okhttp3.OkHttpClient

/**
 * `CoroutineDispatcher` de IO **de este módulo**, por `@Qualifier` de Hilt.
 *
 * No existe `DispatcherProvider` y no se crea (global-constraints §Dispatchers):
 * cuando el dispatcher cruza módulo se declara así, que es lo que ya hace
 * `VisitasIoDispatcher` en `:feature:visitas`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SpeechIoDispatcher

/**
 * El `Call.Factory` **de este módulo**, calificado.
 *
 * Sin el calificador, Dagger encuentra dos `okhttp3.Call.Factory` en el grafo de
 * `:app` —el de `:core:appgate`, que baja el APK, y éste— y falla con
 * `DuplicateBindings`. Calificarlo, y no reusar aquél, es lo correcto: son dos
 * descargas con políticas distintas (una es la compuerta de versión de la app,
 * la otra un modelo opcional) y compartir el cliente ataría una a la otra.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SpeechCallFactory

/**
 * **El único archivo del módulo que ve las dos caras.**
 *
 * Lo que sale de acá hacia la app es un solo [DictadoPort] —el delegador— y un
 * solo [ModeloDeDictadoPort]. Los dos adaptadores de dictado se construyen acá
 * dentro y **nadie más los puede inyectar**: si un ViewModel pudiera pedir
 * `AndroidDictadoAdapter` por su nombre, la propiedad "la UI nunca sabe cuál
 * corre" se perdería en la primera prisa.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object SpeechModule {

    /**
     * El paquete del modelo **`tiny` cuantizado a int8**, medido — no estimado.
     *
     * ## Los tres números salen de la red, no de la memoria de nadie
     *
     * `HEAD` sobre la URL (2026-09-16) contesta `x-linked-size: 43537433` y
     * `x-linked-etag: c2085835…`, que es el SHA-256 del objeto LFS. Ésos son los
     * valores de abajo, tal cual. El `accept-ranges: bytes` de la misma
     * respuesta es lo que hace que la descarga pueda reanudar.
     *
     * ## El mock dice 75 MB y el mock está equivocado
     *
     * "~75 MB" es el peso de `ggml-tiny.bin` **sin cuantizar** (77,691,713 B,
     * medido igual). El modelo que la arquitectura eligió es el **int8**
     * (`q8_0`), que pesa 43,537,433 B — o sea **43.5 MB**. Se respeta la decisión
     * de arquitectura (int8 es lo que hace que whisper rinda en gama media) y
     * se corrige el número, porque un número inventado en la pantalla que
     * existe para decir la verdad sobre el consumo sería el peor lugar para
     * mentir. La pantalla lo deriva de [ModeloDeDictado.tamanoBytes]: no hay un
     * "44" escrito a mano en ninguna parte.
     *
     * **El `.bin` NO está en el repo**: `CLAUDE.md` §5 dice que es público, y
     * 43.5 MB committeados no se pueden desandar.
     */
    @Provides
    @Singleton
    fun modelo(): ModeloDeDictado = ModeloDeDictado(
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q8_0.bin",
        tamanoBytes = 43_537_433L,
        sha256 = "c2085835d3f50733e2ff6e4b41ae8a2b8d8110461e18821b09a15c40c42d1cca"
    )

    @Provides
    @Singleton
    @SpeechIoDispatcher
    fun dispatcher(): kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    /** Un reloj **monótono** para medir cuánto lleva el dictado. No es una fecha. */
    @Provides
    @Singleton
    fun reloj(): () -> Long = { android.os.SystemClock.elapsedRealtime() }

    @Provides
    @Singleton
    fun nativo(): MotorWhisperNativo = WhisperJni()

    /**
     * Cliente propio y **no** el de `:core:network`: `NetworkKillSwitchGuardTest`
     * prohíbe que algo `@Singleton` sostenga un servicio de `ApiProvider.create()`
     * porque eso congelaría el `baseURL` de la flota. Acá no hay servicio ni
     * `baseURL` de la app — la URL del modelo es una constante de este módulo.
     */
    @Provides
    @Singleton
    @SpeechCallFactory
    fun llamadas(): Call.Factory = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun almacen(@ApplicationContext context: Context, telemetry: Telemetry): AlmacenDelModelo =
        AlmacenDelModelo(File(context.filesDir, "dictado"), telemetry)

    @Provides
    @Singleton
    fun grabadora(
        @ApplicationContext context: Context,
        @SpeechIoDispatcher dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): GrabadoraDeAudio = AudioRecordGrabadora(File(context.filesDir, "dictado"), dispatcher)

    @Provides
    @Singleton
    fun descarga(
        @SpeechCallFactory llamadas: Call.Factory,
        almacen: AlmacenDelModelo,
        telemetry: Telemetry,
        @SpeechIoDispatcher dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): DescargaDelModelo = DescargaDelModelo(llamadas, almacen, telemetry, dispatcher)

    /**
     * El puerto que la app inyecta. Los dos adaptadores se arman acá y se
     * entregan al delegador; ninguno sale de este archivo.
     */
    @Provides
    @Singleton
    @Suppress("LongParameterList") // es el cableado del módulo: son las dos caras.
    fun dictado(
        reconocedor: ReconocedorDeAndroid,
        grabadora: GrabadoraDeAudio,
        permiso: PermisoDeMicrofono,
        nativo: MotorWhisperNativo,
        almacen: AlmacenDelModelo,
        telemetry: Telemetry,
        reloj: () -> Long,
        @SpeechIoDispatcher dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): DictadoPort = DictadoSegunElMotor(
        android = AndroidDictadoAdapter(reconocedor, grabadora, permiso, telemetry, reloj),
        whisper = WhisperDictadoAdapter(
            nativo = nativo,
            almacen = almacen,
            grabadora = grabadora,
            permiso = permiso,
            telemetry = telemetry,
            dispatcher = dispatcher
        ),
        nativo = nativo,
        almacen = almacen,
        reconocedorDeAndroid = reconocedor,
        permiso = permiso
    )

    @Provides
    @Singleton
    fun modeloPort(
        almacen: AlmacenDelModelo,
        planificador: PlanificadorDeLaDescarga,
        estado: EstadoDelModeloEnMemoria,
        modelo: ModeloDeDictado,
        nativo: MotorWhisperNativo
    ): ModeloDeDictadoPort = ModeloDeDictadoAdapter(almacen, planificador, estado, modelo, nativo)
}

/** Las costuras con una sola implementación real. `@Binds` y nada más. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SpeechBindings {

    @Binds
    @Singleton
    abstract fun reconocedor(impl: SpeechRecognizerDeAndroid): ReconocedorDeAndroid

    @Binds
    @Singleton
    abstract fun permiso(impl: PermisoDeMicrofonoDelSistema): PermisoDeMicrofono

    @Binds
    @Singleton
    abstract fun planificador(impl: PlanificadorConWorkManager): PlanificadorDeLaDescarga
}
