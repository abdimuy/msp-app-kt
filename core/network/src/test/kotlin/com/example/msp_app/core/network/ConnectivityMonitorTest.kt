package com.example.msp_app.core.network

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.msp_app.core.testing.RobolectricTestBase
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * Cobertura "supremo" (Task 5, Plan 4, política AUDITAR+REESCRIBIR) del
 * [ConnectivityMonitor] reubicado desde `:app`. Usa el shadow real de
 * [ConnectivityManager] de Robolectric (registro/desregistro de
 * [ConnectivityManager.NetworkCallback] genuino) en vez de fakes — es la
 * pieza bajo prueba, no un colaborador a reemplazar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityMonitorTest : RobolectricTestBase() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val connectivityManager: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private lateinit var monitor: ConnectivityMonitor
    private var nextNetId = FIRST_TEST_NET_ID

    @Before
    fun setUp() {
        monitor = ConnectivityMonitor(context)
    }

    // ─── isNetworkAvailable (chequeo síncrono por transporte) ─────────────

    @Test
    fun `isNetworkAvailable es true cuando la red activa tiene transporte WIFI`() {
        setActiveTransport(NetworkCapabilities.TRANSPORT_WIFI)

        assertTrue(monitor.isNetworkAvailable())
    }

    @Test
    fun `isNetworkAvailable es true cuando la red activa tiene transporte CELLULAR`() {
        setActiveTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        assertTrue(monitor.isNetworkAvailable())
    }

    @Test
    fun `isNetworkAvailable es true cuando la red activa tiene transporte ETHERNET`() {
        setActiveTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        assertTrue(monitor.isNetworkAvailable())
    }

    @Test
    fun `isNetworkAvailable es false cuando no hay red activa`() {
        shadowOf(connectivityManager).setActiveNetworkInfo(null)

        assertFalse(monitor.isNetworkAvailable())
    }

    @Test
    fun `isNetworkAvailable es false cuando la red activa no tiene capabilities registradas`() {
        shadowOf(
            connectivityManager
        ).setActiveNetworkInfo(connectedInfo(ConnectivityManager.TYPE_VPN))
        // Deliberadamente sin setNetworkCapabilities: getNetworkCapabilities(network) da null.

        assertFalse(monitor.isNetworkAvailable())
    }

    @Test
    fun `isNetworkAvailable es false si el unico transporte es VPN (no WIFI-CELLULAR-ETHERNET)`() {
        shadowOf(
            connectivityManager
        ).setActiveNetworkInfo(connectedInfo(ConnectivityManager.TYPE_VPN))
        val network = connectivityManager.activeNetwork
            ?: error("el shadow de ConnectivityManager no genero una red activa")
        shadowOf(connectivityManager).setNetworkCapabilities(
            network,
            capabilitiesWith(NetworkCapabilities.TRANSPORT_VPN)
        )

        assertFalse(monitor.isNetworkAvailable())
    }

    @Test
    fun `isNetworkAvailable degrada a false y no propaga si ConnectivityManager no esta disponible`() {
        // Fake por subclase de ContextWrapper (permitido por la politica
        // fakes-only): fuerza el `as ConnectivityManager` interno a fallar
        // con ClassCastException, ejercitando el catch documentado en el
        // KDoc de la clase sin cambiar el valor de retorno observable.
        val brokenContext = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? = if (name == CONNECTIVITY_SERVICE) {
                "no es un ConnectivityManager"
            } else {
                super.getSystemService(
                    name
                )
            }
        }
        val brokenMonitor = ConnectivityMonitor(brokenContext)

        assertFalse(brokenMonitor.isNetworkAvailable())
    }

    // ─── isConnected (Flow reactivo, registro/desregistro de callback) ────

    @Test
    fun `isConnected emite el estado inicial segun isNetworkAvailable al momento de colectar`() =
        runTest {
            setActiveTransport(NetworkCapabilities.TRANSPORT_WIFI)

            monitor.isConnected.test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isConnected registra un callback al colectar y lo desregistra al cancelar`() = runTest {
        setActiveTransport(NetworkCapabilities.TRANSPORT_WIFI)

        assertEquals(0, shadowOf(connectivityManager).networkCallbacks.size)

        monitor.isConnected.test {
            awaitItem() // estado inicial
            assertEquals(1, shadowOf(connectivityManager).networkCallbacks.size)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(0, shadowOf(connectivityManager).networkCallbacks.size)
    }

    @Test
    fun `dos colectores simultaneos registran 2 callbacks y cada cancelacion desregistra solo el suyo`() =
        runTest {
            setActiveTransport(NetworkCapabilities.TRANSPORT_WIFI)

            monitor.isConnected.test {
                awaitItem() // inicial del primer colector

                monitor.isConnected.test {
                    awaitItem() // inicial del segundo colector
                    assertEquals(2, shadowOf(connectivityManager).networkCallbacks.size)
                    cancelAndIgnoreRemainingEvents()
                }

                assertEquals(1, shadowOf(connectivityManager).networkCallbacks.size)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(0, shadowOf(connectivityManager).networkCallbacks.size)
        }

    @Test
    fun `isConnected emite true en onAvailable y false en onLost`() = runTest {
        shadowOf(connectivityManager).setActiveNetworkInfo(null) // inicial desconectado

        monitor.isConnected.test {
            assertFalse(awaitItem())

            val callback = shadowOf(connectivityManager).networkCallbacks.single()
            val network = fakeNetwork()

            callback.onAvailable(network)
            assertTrue(awaitItem())

            callback.onLost(network)
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isConnected en onCapabilitiesChanged exige INTERNET+VALIDATED (mas estricto que isNetworkAvailable)`() =
        runTest {
            shadowOf(connectivityManager).setActiveNetworkInfo(null) // inicial desconectado

            monitor.isConnected.test {
                assertFalse(awaitItem())

                val callback = shadowOf(connectivityManager).networkCallbacks.single()
                val network = fakeNetwork()

                // WIFI con internet pero SIN validar (portal cautivo) -> sigue
                // "false", así que no hay nueva emisión (distinctUntilChanged).
                callback.onCapabilitiesChanged(
                    network,
                    capabilitiesWith(
                        NetworkCapabilities.TRANSPORT_WIFI,
                        internet = true,
                        validated = false
                    )
                )

                // WIFI validado -> SI cambia a true.
                callback.onCapabilitiesChanged(
                    network,
                    capabilitiesWith(
                        NetworkCapabilities.TRANSPORT_WIFI,
                        internet = true,
                        validated = true
                    )
                )
                assertTrue(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isConnected colapsa emisiones consecutivas iguales (distinctUntilChanged)`() = runTest {
        setActiveTransport(NetworkCapabilities.TRANSPORT_WIFI) // inicial true

        monitor.isConnected.test {
            assertTrue(awaitItem())

            val callback = shadowOf(connectivityManager).networkCallbacks.single()
            val network = fakeNetwork()

            // Dos "true" seguidos no deben producir emisiones nuevas: el
            // siguiente awaitItem() de abajo tiene que resolver directo al
            // "false" del onLost, sin un "true" duplicado de por medio.
            callback.onAvailable(network)
            callback.onAvailable(network)

            callback.onLost(network)
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── getInstance (singleton compartido, `:app` lo usa fuera de Hilt) ──

    @Test
    fun `getInstance devuelve siempre la misma instancia`() {
        val first = ConnectivityMonitor.getInstance(context)
        val second = ConnectivityMonitor.getInstance(context)

        assertSame(first, second)
    }

    @Test
    fun `getInstance es seguro bajo llamadas concurrentes (double-checked locking)`() {
        val seen = ConcurrentLinkedQueue<ConnectivityMonitor>()
        val callers = (1..CONCURRENT_CALLERS).map {
            Thread { seen += ConnectivityMonitor.getInstance(context) }
        }

        callers.forEach(Thread::start)
        callers.forEach(Thread::join)

        assertEquals(1, seen.distinct().size)
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private fun setActiveTransport(transportType: Int) {
        shadowOf(
            connectivityManager
        ).setActiveNetworkInfo(connectedInfo(ConnectivityManager.TYPE_WIFI))
        val network = connectivityManager.activeNetwork
            ?: error("el shadow de ConnectivityManager no genero una red activa")
        shadowOf(
            connectivityManager
        ).setNetworkCapabilities(network, capabilitiesWith(transportType))
    }

    private fun connectedInfo(networkType: Int): NetworkInfo = ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.CONNECTED,
        networkType,
        0,
        true,
        NetworkInfo.State.CONNECTED
    )

    private fun capabilitiesWith(
        vararg transportTypes: Int,
        internet: Boolean = false,
        validated: Boolean = false
    ): NetworkCapabilities {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        val shadowCapabilities = shadowOf(capabilities)
        transportTypes.forEach(shadowCapabilities::addTransportType)
        if (internet) shadowCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) {
            shadowCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
        }
        return capabilities
    }

    private fun fakeNetwork(): Network = ShadowNetwork.newInstance(nextNetId++)

    private companion object {
        const val FIRST_TEST_NET_ID = 1000
        const val CONCURRENT_CALLERS = 8
    }
}
