package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.domain.model.PendientesDelTelefono
import com.example.msp_app.feature.visitas.domain.port.PendientesDelTelefonoPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * El aviso de Inicio "N sin enviar · no cambies de teléfono" (E-APP-096). Solo
 * pinta lo que el puerto cuenta. Si la lectura falla se emite su código y se
 * conserva el último conteo: borrar el aviso por un error escondería pendientes
 * reales.
 */
@HiltViewModel
class AvisoDePendientesViewModel @Inject constructor(
    puerto: PendientesDelTelefonoPort,
    private val telemetry: Telemetry
) : ViewModel() {

    val pendientes: StateFlow<PendientesDelTelefono> = puerto.observar()
        .catch { fallo ->
            telemetry.error(
                VisitasTelemetria.CODE_AVISO_PENDIENTES_FALLO,
                fallo::class.java.simpleName
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SUSCRIPCION_MS),
            PendientesDelTelefono()
        )

    private companion object {
        const val SUSCRIPCION_MS = 5_000L
    }
}
