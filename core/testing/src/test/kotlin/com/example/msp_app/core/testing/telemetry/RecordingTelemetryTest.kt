package com.example.msp_app.core.testing.telemetry

import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingTelemetryTest {

    @Test
    fun `starts with no recorded events`() {
        val telemetry = RecordingTelemetry()

        assertTrue(telemetry.recorded.isEmpty())
    }

    @Test
    fun `screenView records a SCREEN_VIEW event with the screen as name`() {
        val clock = FakeClock(Instant.parse("2026-08-09T12:00:00Z"))
        val telemetry = RecordingTelemetry(clock)

        telemetry.screenView("cobranza_detalle")

        val event = telemetry.recorded.single()
        assertEquals(TelemetryEventType.SCREEN_VIEW, event.type)
        assertEquals("cobranza_detalle", event.name)
        assertTrue(event.props.isEmpty())
        assertEquals(Instant.parse("2026-08-09T12:00:00Z"), event.occurredAt)
    }

    @Test
    fun `tap records a TAP event with the element as name and the screen as a prop`() {
        val telemetry = RecordingTelemetry()

        telemetry.tap(screen = "cobranza_detalle", element = "boton_confirmar")

        val event = telemetry.recorded.single()
        assertEquals(TelemetryEventType.TAP, event.type)
        assertEquals("boton_confirmar", event.name)
        assertEquals(mapOf("screen" to "cobranza_detalle"), event.props)
    }

    @Test
    fun `event records an EVENT with its props verbatim`() {
        val telemetry = RecordingTelemetry()

        telemetry.event("pago_registrado", mapOf("forma_cobro" to "efectivo"))

        val event = telemetry.recorded.single()
        assertEquals(TelemetryEventType.EVENT, event.type)
        assertEquals("pago_registrado", event.name)
        assertEquals(mapOf("forma_cobro" to "efectivo"), event.props)
    }

    @Test
    fun `event without props defaults to empty`() {
        val telemetry = RecordingTelemetry()

        telemetry.event("sync_ok")

        assertTrue(telemetry.recorded.single().props.isEmpty())
    }

    @Test
    fun `error records an ERROR with code as name and message folded into props`() {
        val telemetry = RecordingTelemetry()

        telemetry.error(
            code = "red_timeout",
            message = "timeout tras 3 intentos",
            props = mapOf("intentos" to "3")
        )

        val event = telemetry.recorded.single()
        assertEquals(TelemetryEventType.ERROR, event.type)
        assertEquals("red_timeout", event.name)
        assertEquals(
            mapOf("intentos" to "3", "message" to "timeout tras 3 intentos"),
            event.props
        )
    }

    @Test
    fun `records multiple calls in call order`() {
        val telemetry = RecordingTelemetry()

        telemetry.screenView("inicio")
        telemetry.tap(screen = "inicio", element = "boton_ir")
        telemetry.event("sync_ok")
        telemetry.error(code = "red_timeout", message = "sin respuesta")

        val types = telemetry.recorded.map { it.type }
        assertEquals(
            listOf(
                TelemetryEventType.SCREEN_VIEW,
                TelemetryEventType.TAP,
                TelemetryEventType.EVENT,
                TelemetryEventType.ERROR
            ),
            types
        )
    }

    @Test
    fun `counts calls by distinguishing each recorded event`() {
        val telemetry = RecordingTelemetry()

        telemetry.event("a")
        telemetry.event("a")
        telemetry.event("b")

        assertEquals(3, telemetry.recorded.size)
        assertEquals(2, telemetry.recorded.count { it.name == "a" })
        assertEquals(1, telemetry.recorded.count { it.name == "b" })
    }

    @Test
    fun `recorded is a snapshot, not affected by events recorded afterwards`() {
        val telemetry = RecordingTelemetry()
        telemetry.event("primero")
        val snapshot = telemetry.recorded

        telemetry.event("segundo")

        assertEquals(1, snapshot.size)
        assertEquals(2, telemetry.recorded.size)
    }

    @Test
    fun `default clock produces a real Instant, never a frozen placeholder`() {
        val telemetry = RecordingTelemetry()
        val before = Instant.now()

        telemetry.event("sync_ok")

        val after = Instant.now()
        val occurredAt = telemetry.recorded.single().occurredAt
        assertTrue(!occurredAt.isBefore(before) && !occurredAt.isAfter(after))
    }
}
