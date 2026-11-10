package com.example.msp_app.feature.usodedatos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.datos.ServicioDeUsoDeDatos
import com.example.msp_app.core.datos.UsoDelDia
import com.example.msp_app.feature.usodedatos.ConstructorDeResumen
import com.example.msp_app.feature.usodedatos.UsoDeDatosUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** De dónde salen los días (el servicio real, o un doble en las pruebas). */
fun interface FuenteDeUso {
    suspend fun dias(): List<UsoDelDia>
}

private class ServicioComoFuente(private val servicio: ServicioDeUsoDeDatos) : FuenteDeUso {
    override suspend fun dias(): List<UsoDelDia> {
        servicio.muestreador.muestrear() // pone al día el total antes de leer
        return servicio.registro.instantanea()
    }
}

/** Lee el uso al abrir y lo refresca mientras la pantalla está a la vista. Solo lee: no cambia nada. */
@HiltViewModel
class UsoDeDatosViewModel internal constructor(
    private val fuente: FuenteDeUso,
    private val clock: AppClock,
    private val refrescoMs: Long
) : ViewModel() {

    @Inject
    constructor(
        servicio: ServicioDeUsoDeDatos
    ) : this(ServicioComoFuente(servicio), AppClock.System, REFRESCO_MS)

    val estado: StateFlow<UsoDeDatosUiState> = flow {
        while (true) {
            emit(cargar())
            delay(refrescoMs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UsoDeDatosUiState())

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun cargar(): UsoDeDatosUiState = try {
        ConstructorDeResumen.construir(fuente.dias(), AppTime.todayInBusinessZone(clock))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        UsoDeDatosUiState(cargando = false) // leer es solo lectura: ante un fallo, "sin datos"
    }

    private companion object {
        const val REFRESCO_MS = 5_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
