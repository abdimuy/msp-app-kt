package com.example.msp_app.core.context

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.msp_app.core.network.ConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Estado de conectividad para usar con CompositionLocal
 */
data class ConnectivityState(
    val isConnected: Boolean = true,
    val isInitialized: Boolean = false
)

/**
 * CompositionLocal para acceder al estado de conectividad globalmente
 */
val LocalConnectivityState = compositionLocalOf { ConnectivityState() }

/**
 * Composable que observa el estado de conectividad
 * Usar en el nivel más alto de la app (MainActivity)
 */
@Composable
fun rememberConnectivityState(): State<ConnectivityState> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val connectivityMonitor = remember {
        ConnectivityMonitor.getInstance(context)
    }

    val state = remember {
        mutableStateOf(
            ConnectivityState(
                isConnected = connectivityMonitor.isNetworkAvailable(),
                isInitialized = true
            )
        )
    }

    DisposableEffect(lifecycleOwner) {
        var job: Job? = null

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    job = CoroutineScope(Dispatchers.Main).launch {
                        connectivityMonitor.isConnected.collectLatest { isConnected ->
                            state.value = ConnectivityState(
                                isConnected = isConnected,
                                isInitialized = true
                            )
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    job?.cancel()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            job?.cancel()
        }
    }

    return state
}
