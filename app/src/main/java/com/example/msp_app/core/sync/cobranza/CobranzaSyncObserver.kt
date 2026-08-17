package com.example.msp_app.core.sync.cobranza

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.coroutineScope
import com.example.msp_app.workmanager.enqueueCobranzaReconcileNowWorker
import com.example.msp_app.workmanager.enqueueCobranzaReconcilePeriodicWorker

/**
 * Wires a [CobranzaSyncManager] and a [CobranzaSseSubscriber] to the host's
 * lifecycle. Both start on `ON_START` and stop on `ON_STOP`, so the 30s polling
 * loop and SSE streams only run while a screen is foregrounded with the
 * cobrador authenticated.
 *
 * El reconciliador **ya no vive aquí**. Vivía en un bucle atado al ciclo de
 * vida con el `delay` **antes** de la primera vuelta, y el job moría en
 * `ON_STOP`: para correr una sola vez exigía cinco minutos ininterrumpidos de
 * app en primer plano. El uso real de un cobrador son ráfagas de segundos, así
 * que **no corrió nunca en ningún teléfono de la flota** — y con él nunca corrió
 * el rescate por `by-ids`, el único canal sin watermark. Ahora lo agenda
 * WorkManager: `enqueueCobranzaReconcileNowWorker` corre en cada apertura, sin
 * retraso inicial, y `enqueueCobranzaReconcilePeriodicWorker` sostiene la
 * cadencia de respaldo aunque la app se cierre.
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
        val observer = LifecycleEventObserver { owner, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    val scope = owner.lifecycle.coroutineScope
                    manager.start(scope)
                    // Reconciliar primero, esperar después: el trabajo puntual
                    // sale sin retraso inicial y el periódico sólo sostiene la
                    // cadencia. Ambos sobreviven al `ON_STOP`.
                    enqueueCobranzaReconcileNowWorker(context)
                    enqueueCobranzaReconcilePeriodicWorker(context)
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
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            CobranzaSseProvider.get(manager, lifecycleOwner.lifecycle.coroutineScope).stop()
            manager.stop()
        }
    }
}
