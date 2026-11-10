package com.example.msp_app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.msp_app.core.avisos.CanalesDeNotificacion
import com.example.msp_app.core.avisos.KillSwitchAvisos
import com.example.msp_app.core.avisos.encolarReprogramacionDeAvisos
import com.example.msp_app.core.datos.ClasificadorDeServicio
import com.example.msp_app.core.datos.UsoDeDatos
import com.example.msp_app.core.debug.RemoteDbDebugger
import com.example.msp_app.core.diagnosticoenvio.EnvioDiagnostico
import com.example.msp_app.core.fcm.SuscripcionDeZonaProvider
import com.example.msp_app.core.logging.Logger
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider
import com.example.msp_app.core.ubicacion.ConfigDeUbicacion
import com.example.msp_app.core.usodedatos.ReporteDeUsoDeDatosWorker
import com.example.msp_app.core.visitas.agenda.KillSwitchAgenda
import com.example.msp_app.core.visitas.envio.FirestoreModoEnvioVisitas
import com.example.msp_app.workmanager.enqueueClienteSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class MspApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    /**
     * Process-scoped coroutine scope used by background, fire-and-forget work
     * that must survive Activity/Composition recreation (e.g. the session
     * pending-work sync dispatched from AppNavigation).
     *
     * SupervisorJob so a failure in one child does not cancel siblings; IO
     * dispatcher so the default home for DB + network work is the right one.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * On-demand WorkManager initialization: the default `androidx.startup`
     * initializer is removed from AndroidManifest.xml, so WorkManager builds
     * its instance lazily from this [Configuration] the first time
     * `WorkManager.getInstance(context)` is called. `HiltWorkerFactory`
     * returns null for any Worker not annotated `@HiltWorker`, in which case
     * WorkManager falls back to its default reflective constructor — so the
     * 7 existing Workers (plain `(Context, WorkerParameters)`) keep working
     * unchanged.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Inicializar el sistema de logging remoto
        initializeRemoteLogging()

        // Inicializar el sistema de debug remoto de base de datos
        initializeRemoteDbDebugger()

        // Motivo del último fallo de envío (solo diagnóstico, E-APP-063)
        EnvioDiagnostico.instalar(this)

        // Kill-switch del envío de visitas: config/visitas_envio (nunca api_settings)
        FirestoreModoEnvioVisitas.instalar()

        // Canales de los avisos de la agenda (visitas asignadas y compromisos)
        CanalesDeNotificacion.crear(this)

        // Interruptor de la bajada de la agenda: config/visitas_compromisos (apagado por omisión)
        KillSwitchAgenda.instalar()

        // Interruptor de los avisos: config/visitas_avisos (apagado por omisión)
        KillSwitchAvisos.instalar()
        ConfigDeUbicacion.instalar()

        // Las alarmas se reconstruyen al abrir la app (plan visitas P3, Tarea 13)
        encolarReprogramacionDeAvisos(this)

        // Tema FCM de la zona: se suscribe al fijarse y se cambia si cambia (T14)
        suscribirTemaDeZona()

        // Inicializar sincronización periódica de clientes
        enqueueClienteSyncWorker(this)

        // Uso de datos (E-APP-171..174): total real, desglose por servicio y reporte diario.
        UsoDeDatos.instalar(this)
        ReporteDeUsoDeDatosWorker.encolar(this)
    }

    /**
     * Coil carga las fotos y las imágenes de producto con su propio cliente: se le pone
     * el contador de bytes para que ese tráfico no quede en "Mapa, Firebase y otros".
     * Solo agrega un `networkInterceptor` que observa; el resto es el cliente por defecto.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addNetworkInterceptor(
                    UsoDeDatos.interceptor(ClasificadorDeServicio.CLAVE_IMAGENES)
                )
                .build()
        }
        .build()

    private fun suscribirTemaDeZona() {
        val suscripcion = SuscripcionDeZonaProvider.de(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            CobranzaSyncProvider.userContextFlow
                .map { it?.zona }
                .distinctUntilChanged()
                .collect { zona -> if (zona != null) suscripcion.alCambiarZona(zona) }
        }
    }

    private fun initializeRemoteDbDebugger() {
        try {
            RemoteDbDebugger.init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initializeRemoteLogging() {
        try {
            Logger.init(this)

            // Log de inicio de aplicación
            RemoteLogger.getInstance(this).info(
                module = "APP",
                action = "STARTUP",
                message = "Aplicación iniciada",
                data = mapOf(
                    "packageName" to packageName,
                    "versionName" to packageManager.getPackageInfo(packageName, 0).versionName,
                    "versionCode" to packageManager.getPackageInfo(packageName, 0).versionCode
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
