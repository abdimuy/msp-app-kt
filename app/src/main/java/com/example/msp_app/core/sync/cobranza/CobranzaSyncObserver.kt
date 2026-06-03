package com.example.msp_app.core.sync.cobranza

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wires a [CobranzaSyncManager], a [CobranzaReconciler] and a
 * [CobranzaSseSubscriber] to the host's lifecycle. The sync manager and SSE
 * subscriber start on `ON_START` and stop on `ON_STOP`, so the 30s polling
 * loop and SSE streams only run while a screen is foregrounded with the
 * cobrador authenticated.
 *
 * The reconciler runs on a separate periodic job (every
 * [CobranzaReconciler.RECONCILE_INTERVAL_MS] = 5 min). It does NOT fire
 * immediately on `ON_START` — the regular sync just ran and reconcile is
 * purely defensive. The job is cancelled on `ON_STOP`.
 *
 * The SSE subscriber listens to server-push notifications from both the
 * pagos and saldos stream endpoints. On any event it triggers
 * `syncNow()` (debounced). On 503 (feature flag off) it stops retrying and
 * the existing polling+reconcile loop continues covering the functionality.
 *
 * Mount once below the auth gate in the navigation host.
 */
@Composable
fun CobranzaSyncObserver(manager: CobranzaSyncManager) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(lifecycleOwner, manager) {
        var reconcilerJob: Job? = null
        val reconciler = CobranzaReconcilerProvider.get(context)

        val observer = LifecycleEventObserver { owner, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    val scope = owner.lifecycle.coroutineScope
                    manager.start(scope)
                    reconcilerJob = scope.launch(Dispatchers.IO) {
                        while (isActive) {
                            delay(CobranzaReconciler.RECONCILE_INTERVAL_MS)
                            reconciler.reconcileNow()
                        }
                    }
                    // Iniciar SSE después del sync manager para que syncNow()
                    // ya esté disponible cuando llegue el primer evento.
                    val sseSubscriber = CobranzaSseProvider.get(manager, scope)
                    sseSubscriber.start()
                }
                Lifecycle.Event.ON_STOP -> {
                    // Detener SSE primero para que no dispare syncs mientras
                    // el manager se está apagando.
                    CobranzaSseProvider.get(manager, owner.lifecycle.coroutineScope).stop()
                    manager.stop()
                    reconcilerJob?.cancel()
                    reconcilerJob = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            CobranzaSseProvider.get(manager, lifecycleOwner.lifecycle.coroutineScope).stop()
            manager.stop()
            reconcilerJob?.cancel()
        }
    }
}
