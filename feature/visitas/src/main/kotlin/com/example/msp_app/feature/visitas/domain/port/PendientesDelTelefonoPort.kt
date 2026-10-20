package com.example.msp_app.feature.visitas.domain.port

import com.example.msp_app.feature.visitas.domain.model.PendientesDelTelefono
import kotlinx.coroutines.flow.Flow

/** Los pendientes del teléfono para el aviso de Inicio (E-APP-096). Solo lectura. */
interface PendientesDelTelefonoPort {
    /** Emite de nuevo cada vez que Room cambia alguna de las tablas que cuenta. */
    fun observar(): Flow<PendientesDelTelefono>
}
