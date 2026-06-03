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
 * Wires a [CobranzaSyncManager] and a [CobranzaReconciler] to the host's
 * lifecycle. The sync manager starts on `ON_START` and stops on `ON_STOP`,
 * so the 30s polling loop only runs while a screen is foregrounded with
 * the cobrador authenticated.
 *
 * The reconciler runs on a separate periodic job (every
 * [CobranzaReconciler.RECONCILE_INTERVAL_MS] = 5 min). It does NOT fire
 * immediately on `ON_START` — the regular sync just ran and reconcile is
 * purely defensive. The job is cancelled on `ON_STOP`.
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
                }
                Lifecycle.Event.ON_STOP -> {
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
            manager.stop()
            reconcilerJob?.cancel()
        }
    }
}
