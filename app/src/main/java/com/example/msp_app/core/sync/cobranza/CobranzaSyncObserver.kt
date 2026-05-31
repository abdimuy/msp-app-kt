package com.example.msp_app.core.sync.cobranza

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.coroutineScope

/**
 * Wires a [CobranzaSyncManager] to the host's lifecycle: the manager
 * starts on `ON_START` and stops on `ON_STOP`, so the 30s polling loop
 * only runs while a screen is foregrounded with the cobrador authenticated.
 *
 * Mount once below the auth gate in the navigation host.
 */
@Composable
fun CobranzaSyncObserver(manager: CobranzaSyncManager) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, manager) {
        val observer = LifecycleEventObserver { owner, event ->
            when (event) {
                Lifecycle.Event.ON_START -> manager.start(owner.lifecycle.coroutineScope)
                Lifecycle.Event.ON_STOP -> manager.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            manager.stop()
        }
    }
}
