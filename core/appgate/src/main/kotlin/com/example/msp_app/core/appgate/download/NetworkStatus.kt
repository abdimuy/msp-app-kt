package com.example.msp_app.core.appgate.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Qué tipo de red hay, con el único corte que le importa a la compuerta: si
 * los megas los paga el usuario o no.
 */
enum class NetworkStatus {
    /** Sin red. No se puede hacer nada, y hay que decirlo con todas sus letras. */
    OFFLINE,

    /** Datos móviles. Se descarga solo si él lo pide, sabiendo el peso. */
    METERED,

    /** Wifi. Aquí sí baja sola. */
    UNMETERED
}

/**
 * Puerto de conectividad de este módulo.
 *
 * No se reusa `ConnectivityMonitor` de `:core:network`: aquel responde
 * "¿hay red?" y esta compuerta necesita "¿quién paga los megas?"
 * (`NET_CAPABILITY_NOT_METERED`), que aquel no expone. Ensanchar su API
 * pública tocaría los ~5 fakes que ya lo subclasean en `:app`.
 */
fun interface NetworkStatusProvider {
    fun observe(): Flow<NetworkStatus>
}

/**
 * Implementación sobre [ConnectivityManager]. Cold: cada colector registra su
 * propio callback y lo desregistra al cancelar ([awaitClose]) — mismo patrón
 * que `ConnectivityMonitor.isConnected`.
 */
class AndroidNetworkStatusProvider(private val context: Context) : NetworkStatusProvider {

    private val connectivityManager: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun observe(): Flow<NetworkStatus> = callbackFlow {
        val manager = connectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(manager.currentStatus())
            }

            override fun onLost(network: Network) {
                trySend(manager.currentStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                trySend(capabilities.toStatus())
            }
        }

        manager.registerDefaultNetworkCallback(callback)
        trySend(manager.currentStatus())

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

private fun ConnectivityManager.currentStatus(): NetworkStatus {
    val network = activeNetwork ?: return NetworkStatus.OFFLINE
    return getNetworkCapabilities(network)?.toStatus() ?: NetworkStatus.OFFLINE
}

private fun NetworkCapabilities.toStatus(): NetworkStatus = when {
    !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkStatus.OFFLINE
    hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkStatus.UNMETERED
    else -> NetworkStatus.METERED
}
