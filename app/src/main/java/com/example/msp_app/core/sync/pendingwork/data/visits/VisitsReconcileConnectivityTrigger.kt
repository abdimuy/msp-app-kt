package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.network.ConnectivityMonitor
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/**
 * Wires [ConnectivityMonitor] to a reconnection callback — the visitas
 * analogue of [com.example.msp_app.core.sync.cobranza.CobranzaSyncManager
 * .connectivityObserver], extracted as its own small class instead of
 * copy-pasted so it is unit-testable without Robolectric/Compose: production
 * wiring just needs to call [observe] from some long-lived coroutine and
 * supply `ConnectivityMonitor.getInstance(context)` (see
 * `VisitsReconcileObserver` in `AppNavigation.kt`).
 *
 * `drop(1)` skips [ConnectivityMonitor.isConnected]'s synchronous replay of
 * the CURRENT state on subscribe — that is not a "change", and reacting to it
 * would double-fire alongside the app-open trigger every single time (this is
 * exactly the reasoning `CobranzaSyncManager.connectivityObserver` documents
 * for its own `drop(1)`). `filter { it }` means only a transition INTO
 * connected calls [onReconnected] — going offline has nothing to reconcile.
 *
 * This class does not itself prevent two reconnections from racing an app-open
 * trigger; that is [com.example.msp_app.core.common.sync.pendingwork.domain
 * .usecases.TriggerVisitsReconciliationUseCase]'s job, and [onReconnected] is
 * expected to call into the same process-wide instance of it (see
 * [VisitsReconcileTriggerProvider]) so the guard actually applies.
 */
class VisitsReconcileConnectivityTrigger(
    private val connectivity: ConnectivityMonitor,
    private val onReconnected: suspend () -> Unit
) {
    suspend fun observe() {
        connectivity.isConnected
            .drop(1)
            .filter { online -> online }
            .collect { onReconnected() }
    }
}
