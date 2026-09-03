package com.example.msp_app.core.sync.pendingwork

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.coroutineScope
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.sync.pendingwork.data.visits.VisitsReconcileConnectivityTrigger
import com.example.msp_app.core.sync.pendingwork.data.visits.VisitsReconcileTriggerProvider
import com.example.msp_app.core.sync.visitas.VisitasSseProvider
import com.example.msp_app.workmanager.enqueueVisitsReconcileNowWorker
import com.example.msp_app.workmanager.enqueueVisitsReconcilePeriodicWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "VisitsReconcileObserver"

/**
 * Mounts Task 11's three visitas-reconciler triggers for the life of an
 * authenticated session. Sibling to
 * [com.example.msp_app.core.sync.cobranza.CobranzaSyncObserver], deliberately
 * NOT merged into it: that one is gated on `ZONA_CLIENTE_ID > 0` (cobranza is
 * zone-scoped), while pending visitas are not — a collector with no zone
 * assigned yet can still have visitas to reconcile. Call once, from
 * `AppNavigation.kt`, for any authenticated user.
 *
 * - **App open**: [enqueueVisitsReconcileNowWorker] on first composition —
 *   `LaunchedEffect(Unit)` fires exactly once per time this composable enters
 *   composition (i.e. once per login; see the call site's key).
 * - **Periodic**: [enqueueVisitsReconcilePeriodicWorker], same call — `KEEP`
 *   makes re-registering across recompositions/logins a no-op once the
 *   15-minute backstop already exists.
 * - **Connectivity**: [VisitsReconcileConnectivityTrigger] collects
 *   [ConnectivityMonitor.isConnected] only while the host is `ON_START`, same
 *   lifecycle boundary `CobranzaSyncObserver` uses for its own connectivity
 *   observer — no NetworkCallback stays registered once the screen backgrounds.
 *
 * All three ultimately call
 * [com.example.msp_app.core.common.sync.pendingwork.domain.usecases
 * .TriggerVisitsReconciliationUseCase.execute] through the SAME
 * [VisitsReconcileTriggerProvider] singleton, so overlapping triggers — the
 * app opening right as connectivity returns, a periodic tick landing mid-run —
 * collapse into one reconciliation. See that use case's KDoc and tests for
 * the guard itself.
 */
@Composable
fun VisitsReconcileObserver() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        registerVisitsReconcileWorkManagerTriggers(
            registerNow = { enqueueVisitsReconcileNowWorker(context) },
            registerPeriodic = { enqueueVisitsReconcilePeriodicWorker(context) }
        )
    }

    DisposableEffect(lifecycleOwner) {
        var connectivityJob: Job? = null
        val observer = LifecycleEventObserver { owner, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // Cuarto disparador (Task 12): el push del servidor. Se
                    // monta y desmonta en el MISMO límite de ciclo de vida que
                    // el de conectividad — un stream SSE abierto con la app en
                    // background es una conexión sostenida sin nadie que la
                    // aproveche. Un fallo al arrancarlo no puede tumbar la
                    // composición ni impedir el disparador de conectividad de
                    // abajo, que es el que cubre la correctitud.
                    runCatching {
                        VisitasSseProvider.get(context, owner.lifecycle.coroutineScope).start()
                    }.onFailure { e ->
                        Log.w(TAG, "no se pudo arrancar el stream SSE de visitas", e)
                    }

                    val trigger = VisitsReconcileConnectivityTrigger(
                        connectivity = ConnectivityMonitor.getInstance(context)
                    ) {
                        VisitsReconcileTriggerProvider.get(context).execute()
                    }
                    connectivityJob = owner.lifecycle.coroutineScope.launch {
                        runCatching { trigger.observe() }
                            .onFailure { e ->
                                // Defensa: `observe()` en si no deberia lanzar
                                // (ConnectivityMonitor.isConnected ya degrada a
                                // `false` en vez de lanzar — ver su KDoc), pero
                                // que un fallo aqui no tumbe la composicion.
                                Log.w(TAG, "connectivity trigger fallo de forma inesperada", e)
                            }
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    runCatching {
                        VisitasSseProvider.get(context, owner.lifecycle.coroutineScope).stop()
                    }.onFailure { e ->
                        Log.w(TAG, "no se pudo detener el stream SSE de visitas", e)
                    }
                    connectivityJob?.cancel()
                    connectivityJob = null
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            connectivityJob?.cancel()
        }
    }
}

/**
 * Registers the app-open and periodic `WorkManager` triggers **independently**
 * — this is NOT `registerNow(); registerPeriodic()` in a shared try/catch, on
 * purpose. A shared block (or no guard at all) means a single throw out of
 * [registerNow] — e.g. a transient `WorkManager` hiccup — silently prevents
 * [registerPeriodic] from ever running, and with it goes the session's entire
 * 15-minute backstop, with nothing in the logs to say so. The periodic worker
 * is the trigger that exists specifically to catch what the other two miss;
 * losing it silently is the one failure mode this whole task exists to
 * eliminate, so each enqueue gets its own `runCatching` + `Log.w` — same
 * "defense in depth" shape as the `SessionSync` block and the connectivity
 * path in [VisitsReconcileObserver].
 *
 * A free function, not inlined into the `LaunchedEffect` above, specifically
 * so this independence is unit-testable without Robolectric/Compose: a test
 * passes a throwing [registerNow] and a recording [registerPeriodic] and
 * asserts the second still ran. See `VisitsReconcileObserverTriggersTest`.
 */
@VisibleForTesting
internal fun registerVisitsReconcileWorkManagerTriggers(
    registerNow: () -> Unit,
    registerPeriodic: () -> Unit
) {
    runCatching(registerNow)
        .onFailure { e -> Log.w(TAG, "no se pudo encolar el disparador de apertura", e) }
    runCatching(registerPeriodic)
        .onFailure { e -> Log.w(TAG, "no se pudo encolar el disparador periodico", e) }
}
