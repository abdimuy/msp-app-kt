package com.example.msp_app.feature.ventacorreccion.data.fake

import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.ventacorreccion.domain.port.RelojPort

/**
 * Fake de [RelojPort] — envuelve [FakeClock] (la fuente única de tiempo de prueba del repo,
 * nunca reloj real) para que las pruebas de esta feature usen el mismo `clock.advance(...)`
 * idiomático que el resto del repo.
 */
class FakeReloj(private val clock: FakeClock) : RelojPort {
    override fun ahoraEpochMillis(): Long = clock.now().toEpochMilli()
}
