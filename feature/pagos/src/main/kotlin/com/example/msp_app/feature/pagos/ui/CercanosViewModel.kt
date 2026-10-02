package com.example.msp_app.feature.pagos.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.domain.port.PrivacidadPort
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * **La cartera por cliente, para pintar los cercanos de Inicio con la MISMA
 * tarjeta de la lista de clientes** (decisión del dueño del 2026-10-02).
 *
 * Inicio ya sabe QUIÉNES están cerca y a qué distancia (`nearbyClientsFrom`); lo
 * que no tenía era cada cliente con sus ventas y el estado del periodo, que es lo
 * que pinta [FilaDeCliente]. Se arma con el mismo [ReunirCartera] de la lista,
 * así que las dos pantallas no pueden contar cosas distintas del mismo cliente.
 *
 * Un fallo al leer no tumba Inicio: la cartera se queda vacía y la sección cae a
 * su renglón de siempre.
 */
@HiltViewModel
class CercanosViewModel @Inject constructor(
    private val reunirCartera: ReunirCartera,
    privacidad: PrivacidadPort,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val porCliente = MutableStateFlow<Map<Int, ClienteEnLista>>(emptyMap())

    /** Cada cliente de la cartera por su `clienteId`. */
    val clientes: StateFlow<Map<Int, ClienteEnLista>> = porCliente.asStateFlow()

    /** "Esconder cantidades", el mismo de la lista. */
    val montosOcultos: StateFlow<Boolean> = privacidad.ocultos
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SUSCRIPCION_MS),
            privacidad.ocultosAhora()
        )

    init {
        recargar()
    }

    /** Vuelve a leer la cartera; Inicio la llama al reanudarse. */
    @Suppress("TooGenericExceptionCaught") // Room o lo que sea: Inicio no se tumba
    fun recargar() {
        viewModelScope.launch {
            porCliente.value = try {
                withContext(io) { reunirCartera().clientes.associateBy { it.clienteId } }
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (fallo: Exception) {
                Log.w(TAG, "no se pudo leer la cartera para los cercanos", fallo)
                emptyMap()
            }
        }
    }

    private companion object {
        const val SUSCRIPCION_MS = 5_000L
        const val TAG = "Cercanos"
    }
}

/**
 * Un cliente cercano pintado con la tarjeta de la lista de clientes, con la
 * distancia en el encabezado. Trae su propio [MspTheme] porque Inicio todavía no
 * vive dentro de él; los toques son los de la lista: el encabezado abre el
 * cliente y cada venta abre esa venta.
 */
@Composable
fun TarjetaDeCercano(
    cliente: ClienteEnLista,
    distancia: String,
    montosOcultos: Boolean,
    onAbrirCliente: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    MspTheme(animateColors = false) {
        FilaDeCliente(
            cliente = cliente,
            onAbrirCliente = onAbrirCliente,
            modifier = modifier,
            montosOcultos = montosOcultos,
            onAbrirVenta = onAbrirVenta,
            distancia = distancia
        )
    }
}
