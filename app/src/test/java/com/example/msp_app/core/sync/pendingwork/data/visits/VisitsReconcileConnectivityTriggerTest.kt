package com.example.msp_app.core.sync.pendingwork.data.visits

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.network.ConnectivityMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 11 — proves the connectivity trigger fires on a restore, and only on a
 * restore. [ConnectivityMonitor] is `open class` / `open val isConnected`
 * expressly so `:app` can subclass it as a fake in tests (its own KDoc names
 * the mechanism) — no MockK, per the repo-wide fakes-only rule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class VisitsReconcileConnectivityTriggerTest {

    private class ScriptedConnectivity(
        private val emissions: MutableSharedFlow<Boolean>
    ) : ConnectivityMonitor(ApplicationProvider.getApplicationContext()) {
        override val isConnected: Flow<Boolean> = emissions
    }

    @Test
    fun `una reconexion dispara el callback exactamente una vez`() = runTest {
        val emissions = MutableSharedFlow<Boolean>(replay = 0)
        var calls = 0
        val trigger = VisitsReconcileConnectivityTrigger(
            connectivity = ScriptedConnectivity(emissions),
            onReconnected = { calls++ }
        )

        val job = launch { trigger.observe() }
        runCurrent()

        // El reemplazo sincronico de "estado actual" al suscribirse NO pasa
        // por aqui: este fake no lo emite (a diferencia del real, que hace
        // `trySend(isNetworkAvailable())` — ver su KDoc), asi que `drop(1)`
        // se prueba con el primer evento real de abajo.
        emissions.emit(false) // se pierde por el drop(1) — modela el replay inicial
        emissions.emit(true) // la reconexion real
        runCurrent()

        assertEquals(1, calls)
        job.cancel()
    }

    @Test
    fun `perder la señal no dispara nada`() = runTest {
        val emissions = MutableSharedFlow<Boolean>(replay = 0)
        var calls = 0
        val trigger = VisitsReconcileConnectivityTrigger(
            connectivity = ScriptedConnectivity(emissions),
            onReconnected = { calls++ }
        )

        val job = launch { trigger.observe() }
        runCurrent()

        emissions.emit(true) // consumido por el drop(1)
        emissions.emit(false) // se va sin red — nada que reconciliar
        runCurrent()

        assertEquals(0, calls)
        job.cancel()
    }

    @Test
    fun `varias reconexiones seguidas disparan una vez cada una`() = runTest {
        val emissions = MutableSharedFlow<Boolean>(replay = 0)
        var calls = 0
        val trigger = VisitsReconcileConnectivityTrigger(
            connectivity = ScriptedConnectivity(emissions),
            onReconnected = { calls++ }
        )

        val job = launch { trigger.observe() }
        runCurrent()

        emissions.emit(false) // drop(1)
        emissions.emit(true)
        emissions.emit(false)
        emissions.emit(true)
        runCurrent()

        assertEquals(2, calls)
        job.cancel()
    }
}
