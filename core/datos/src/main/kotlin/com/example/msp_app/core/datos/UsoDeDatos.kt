package com.example.msp_app.core.datos

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Process
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Punto de entrada del proceso. El [registro] existe desde el primer acceso (sin
 * `Context`) para que cualquier `OkHttpClient` pueda llevar [interceptor] antes de
 * que la app termine de arrancar; [instalar] activa el muestreo y la persistencia.
 *
 * ## Cómo se separan móvil/Wi-Fi y primer/segundo plano (decisión, E-APP-172)
 *
 * NO se usa `NetworkStatsManager`: en Android 6 a 9 (minSdk de la app = 24) consultar
 * tráfico móvil exige el `subscriberId`, que pide `READ_PHONE_STATE` (permiso peligroso),
 * y el brief prohíbe pedirle permisos especiales al cobrador. Se degrada a: tipo de red
 * activa en cada muestra de `TrafficStats` (`ConnectivityManager`, callback de la red
 * por defecto) y plano con el contador de actividades iniciadas. Sin permisos en
 * ninguna versión.
 */
object UsoDeDatos {
    private const val ARCHIVO = "uso_de_datos.json"

    /** Registro global: lo comparten todos los interceptores del proceso. */
    val registro: RegistroDeUso = RegistroDeUso()

    fun interceptor(claveFija: String? = null): InterceptorDeUsoDeDatos =
        InterceptorDeUsoDeDatos(registro, claveFija)

    @Volatile private var servicio: ServicioDeUsoDeDatos? = null
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** El servicio del proceso (lo crea si hace falta, sin iniciar el ciclo). */
    fun servicio(context: Context): ServicioDeUsoDeDatos = servicio ?: synchronized(this) {
        servicio ?: crear(context.applicationContext).also { servicio = it }
    }

    private fun crear(app: Context): ServicioDeUsoDeDatos {
        val muestreador = MuestreadorDeTrafico(
            FuenteDeTraficoDelSistema(Process.myUid()),
            registro,
            TipoDeRed.SIN_RED,
            primerPlanoInicial = false
        )
        return ServicioDeUsoDeDatos(
            registro,
            muestreador,
            AlmacenEnArchivo(File(app.filesDir, ARCHIVO))
        )
    }

    /** Llamar una vez, desde `Application.onCreate`. Nunca lanza. */
    @Suppress("TooGenericExceptionCaught")
    fun instalar(app: Application) {
        try {
            val servicio = servicio(app)
            servicio.iniciarCiclo(alcance)
            vigilarPlano(app, servicio)
            vigilarRed(app, servicio.muestreador)
        } catch (e: Exception) {
            android.util.Log.w("UsoDeDatos", "no se pudo instalar el contador", e)
        }
    }

    private fun vigilarPlano(app: Application, servicio: ServicioDeUsoDeDatos) {
        val contador = ContadorDeActividades { primerPlano ->
            servicio.muestreador.cambiarPlano(primerPlano)
            if (!primerPlano) alcance.launch { servicio.guardarAhora() }
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = contador.iniciada()
            override fun onActivityStopped(activity: Activity) = contador.detenida()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun vigilarRed(app: Application, muestreador: MuestreadorDeTrafico) {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            @Volatile private var actual: TipoDeRed? = null

            private fun fijar(nueva: TipoDeRed) {
                if (nueva == actual) return
                actual = nueva
                muestreador.cambiarRed(nueva)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                fijar(
                    ClasificadorDeRed.clasificar(
                        wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                        celular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    )
                )
            }

            override fun onLost(network: Network) = fijar(TipoDeRed.SIN_RED)
        })
    }
}
