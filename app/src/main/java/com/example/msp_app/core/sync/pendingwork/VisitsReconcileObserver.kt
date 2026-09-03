package com.example.msp_app.core.sync.pendingwork

import android.util.Log
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
        enqueueVisitsReconcileNowWorker(context)
        enqueueVisitsReconcilePeriodicWorker(context)
    }

    DisposableEffect(lifecycleOwner) {
        var connectivityJob: Job? = null
        val observer = LifecycleEventObserver { owner, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
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
