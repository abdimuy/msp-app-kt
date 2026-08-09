package com.example.msp_app.core.testing.telemetry

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.telemetry.TelemetryEvent
import com.example.msp_app.core.telemetry.TelemetryEventType
import java.util.Collections

/**
 * Fake in-memory de [Telemetry] que graba cada llamada como un
 * [TelemetryEvent] consultable, en orden, para que los tests de un módulo
 * consumidor afirmen "se emitió el evento X una vez" sin mocks.
 *
 * Mapeo de cada método del puerto al VO — ver el KDoc de [TelemetryEvent]
 * para el porqué:
 * - `screenView(screen)` → `SCREEN_VIEW` con `name = screen`.
 * - `tap(screen, element)` → `TAP` con `name = element`, `props["screen"]`.
 * - `event(name, props)` → `EVENT` con `name`/`props` tal cual.
 * - `error(code, message, props)` → `ERROR` con `name = code`,
 *   `props + ("message" to message)`.
 *
 * [clock] por defecto es [AppClock.System] (seguro para uso incidental),
 * pero los tests que necesiten timestamps deterministas deben pasar un
 * `FakeClock` explícito.
 *
 * Thread-safe (`Collections.synchronizedList`) siguiendo el mismo patrón que
 * `RecordingSessionSyncObserver`, por si un consumidor emite telemetría
 * desde corrutinas concurrentes.
 */
class RecordingTelemetry(private val clock: AppClock = AppClock.System) : Telemetry {

    private val mutableRecorded: MutableList<TelemetryEvent> = Collections.synchronizedList(
        mutableListOf()
    )

    val recorded: List<TelemetryEvent>
        get() = mutableRecorded.toList()

    override fun screenView(screen: String) {
        record(type = TelemetryEventType.SCREEN_VIEW, name = screen)
    }

    override fun tap(screen: String, element: String) {
        record(type = TelemetryEventType.TAP, name = element, props = mapOf("screen" to screen))
    }

    override fun event(name: String, props: Map<String, String>) {
        record(type = TelemetryEventType.EVENT, name = name, props = props)
    }

    override fun error(code: String, message: String, props: Map<String, String>) {
        record(type = TelemetryEventType.ERROR, name = code, props = props + ("message" to message))
    }

    private fun record(
        type: TelemetryEventType,
        name: String,
        props: Map<String, String> = emptyMap()
    ) {
        mutableRecorded += TelemetryEvent(type = type, name = name, occurredAt = clock.now(), props = props)
    }
}
