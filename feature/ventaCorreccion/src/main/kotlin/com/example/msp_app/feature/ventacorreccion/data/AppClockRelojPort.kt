package com.example.msp_app.feature.ventacorreccion.data

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.ventacorreccion.domain.port.RelojPort

/** Implementación de producción de [RelojPort]: delega en [AppClock] (por defecto, el real). */
class AppClockRelojPort(private val clock: AppClock = AppClock.System) : RelojPort {
    override fun ahoraEpochMillis(): Long = clock.now().toEpochMilli()
}
