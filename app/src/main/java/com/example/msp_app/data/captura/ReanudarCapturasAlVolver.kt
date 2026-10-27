package com.example.msp_app.data.captura

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.msp_app.feature.pagos.domain.port.ReanudarCapturasPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * E-APP-119 (D15, regla del dueño "nada se pierde"): cada vez que la app vuelve
 * a primer plano con sesión, reanuda las visitas y garantías que el cobrador
 * capturó y que no llegaron a escribirse. Se monta una vez, desde
 * `AppNavigation`, sólo con usuario autenticado.
 *
 * Primer plano a propósito (ver [ReanudadorDeCapturas]): la visita escribe
 * `sales` y el diseño no deja hacerlo desde un worker.
 */
@Composable
fun ReanudarCapturasAlVolver(viewModel: ReanudarCapturasViewModel = hiltViewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.reanudar()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** Una reanudación a la vez; la de un regreso que llega mientras otra corre se omite. */
@HiltViewModel
class ReanudarCapturasViewModel @Inject constructor(
    private val reanudarCapturas: ReanudarCapturasPort
) : ViewModel() {

    private var enCurso: Job? = null

    @Suppress("TooGenericExceptionCaught") // reanudar nunca tumba la app.
    fun reanudar() {
        if (enCurso?.isActive == true) return
        enCurso = viewModelScope.launch {
            try {
                reanudarCapturas.reanudar()
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (_: Throwable) {
                // Siguen visibles en el aviso.
            }
        }
    }
}
