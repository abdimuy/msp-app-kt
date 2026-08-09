package com.example.msp_app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TAG = "ConnectivityMonitor"

/**
 * Monitor reactivo de conectividad — reubicado desde `:app` a `:core:network`
 * (Task 5, Plan 4; ver `docs/superpowers/plans/DISPATCH-CONVENTIONS.md`,
 * política AUDITAR+REESCRIBIR). El *package* no cambia
 * (`com.example.msp_app.core.network`), solo el módulo dueño — comportamiento
 * observable IDÉNTICO al original de `:app`.
 *
 * `open class` / `open val isConnected` / `open fun isNetworkAvailable`: NO es
 * un artefacto accidental de diseño — `:app` ya subclasea esta clase como
 * fake en tests (`CobranzaReconcilerTest`, `CobranzaSyncManagerTest`,
 * `CobranzaSyncReenqueueTest`, y los e2e instrumentados
 * `CobranzaDurableQueueE2ETest`/`CobranzaSelfHealTwinE2ETest`), que es el
 * mecanismo "fakes-only" (sin MockK) de este repo para test doubles de
 * conectividad. Cambiar esa forma pública rompería esos ~5 sitios en `:app`.
 *
 * Auditoría (Task 5): [isNetworkAvailable] silenciaba la excepción sin dejar
 * rastro (`catch (e: Exception) { false }`, `e` sin usar). Bajo `msp.detekt`
 * (recién aplicado a este código — `:app` no corría detekt) eso dispara
 * `SwallowedException`. Se agrega el log ([Log.w]) para que el diagnóstico
 * quede en logcat SIN cambiar el valor de retorno (sigue `false`, mismo
 * comportamiento observable de antes). El catch genérico se mantiene (con
 * `@Suppress` documentado): las llamadas a [ConnectivityManager] pueden
 * lanzar en firmwares OEM con bugs conocidos, y degradar a "sin red" es la
 * respuesta segura — no hay un tipo de excepción único y estable al que
 * acotar sin arriesgar dejar pasar un crash real no documentado de algún
 * fabricante.
 */
open class ConnectivityMonitor(private val context: Context) {

    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /**
     * Flow que emite cambios en el estado de conectividad. Cold: cada
     * colector registra su propio [ConnectivityManager.NetworkCallback] y lo
     * desregistra al cancelar la colección ([awaitClose]) — sin fuga de
     * callbacks aunque haya varios colectores simultáneos.
     */
    open val isConnected: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val hasInternet = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                ) && capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
                trySend(hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emite el estado inicial con el mismo chequeo síncrono por
        // transporte que [isNetworkAvailable] (NO el criterio validado de
        // `onCapabilitiesChanged` de arriba) — comportamiento original
        // preservado tal cual, ver nota de auditoría en el KDoc de clase.
        trySend(isNetworkAvailable())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Verifica síncronamente si hay red disponible (transporte WIFI/CELLULAR/
     * ETHERNET activo). NO exige `NET_CAPABILITY_VALIDATED` — a propósito,
     * mismo criterio (más laxo que el de [isConnected]) que el original.
     */
    @Suppress("TooGenericExceptionCaught")
    open fun isNetworkAvailable(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo determinar el estado de conectividad", e)
            false
        }
    }

    companion object {
        @Volatile
        private var instance: ConnectivityMonitor? = null

        fun getInstance(context: Context): ConnectivityMonitor {
            return instance ?: synchronized(this) {
                instance ?: ConnectivityMonitor(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
