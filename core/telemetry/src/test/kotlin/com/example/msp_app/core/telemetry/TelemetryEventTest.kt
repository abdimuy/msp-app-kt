package com.example.msp_app.core.telemetry

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryEventTest {

    private val someInstant: Instant = Instant.parse("2026-08-09T12:00:00Z")

    @Test
    fun `accepts a lowercase snake_case name`() {
        val event = TelemetryEvent(
            type = TelemetryEventType.EVENT,
            name = "pago_registrado",
            occurredAt = someInstant
        )

        assertEquals("pago_registrado", event.name)
    }

    @Test
    fun `accepts dots for namespaced names`() {
        val event = TelemetryEvent(
            type = TelemetryEventType.TAP,
            name = "cobranza_detalle.boton_confirmar",
            occurredAt = someInstant
        )

        assertEquals("cobranza_detalle.boton_confirmar", event.name)
    }

    @Test
    fun `accepts digits in the name`() {
        val event = TelemetryEvent(
            type = TelemetryEventType.SCREEN_VIEW,
            name = "pantalla_v2",
            occurredAt = someInstant
        )

        assertEquals("pantalla_v2", event.name)
    }

    @Test
    fun `rejects a blank name`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEvent(type = TelemetryEventType.EVENT, name = "", occurredAt = someInstant)
        }
    }

    @Test
    fun `rejects a name with only whitespace`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEvent(type = TelemetryEventType.EVENT, name = "   ", occurredAt = someInstant)
        }
    }

    @Test
    fun `rejects uppercase letters`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEvent(
                type = TelemetryEventType.EVENT,
                name = "PagoRegistrado",
                occurredAt = someInstant
            )
        }
    }

    @Test
    fun `rejects spaces`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEvent(
                type = TelemetryEventType.EVENT,
                name = "pago registrado",
                occurredAt = someInstant
            )
        }
    }

    @Test
    fun `rejects free text that looks like user input`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEvent(
                type = TelemetryEventType.ERROR,
                name = "El cliente Juan Perez no pudo pagar",
                occurredAt = someInstant
            )
        }
    }

    @Test
    fun `props defaults to an empty map`() {
        val event =
            TelemetryEvent(
                type = TelemetryEventType.SCREEN_VIEW,
                name = "inicio",
                occurredAt = someInstant
            )

        assertTrue(event.props.isEmpty())
    }

    @Test
    fun `props holds arbitrary static key-value pairs`() {
        val event = TelemetryEvent(
            type = TelemetryEventType.ERROR,
            name = "red_timeout",
            occurredAt = someInstant,
            props = mapOf("intentos" to "3", "resultado" to "fallo")
        )

        assertEquals(mapOf("intentos" to "3", "resultado" to "fallo"), event.props)
    }

    @Test
    fun `props has value semantics, two separately-built equal maps compare equal`() {
        val a = TelemetryEvent(
            type = TelemetryEventType.EVENT,
            name = "sync_ok",
            occurredAt = someInstant,
            props = mapOf("resultado" to "ok")
        )
        val b = TelemetryEvent(
            type = TelemetryEventType.EVENT,
            name = "sync_ok",
            occurredAt = someInstant,
            props = mapOf("resultado" to "ok")
        )

        assertEquals(a, b)
        assertEquals(a.props, b.props)
    }

    @Test
    fun `occurredAt comes from the caller, never from a wall-clock default`() {
        val event =
            TelemetryEvent(
                type = TelemetryEventType.EVENT,
                name = "sync_ok",
                occurredAt = someInstant
            )

        assertEquals(someInstant, event.occurredAt)
    }

    @Test
    fun `two events with equal fields are structurally equal`() {
        val a =
            TelemetryEvent(type = TelemetryEventType.TAP, name = "boton", occurredAt = someInstant)
        val b =
            TelemetryEvent(type = TelemetryEventType.TAP, name = "boton", occurredAt = someInstant)

        assertEquals(a, b)
    }

    @Test
    fun `all 4 taxonomy types are the base spec set, no more`() {
        val expected = setOf("SCREEN_VIEW", "TAP", "EVENT", "ERROR")
        val actual = TelemetryEventType.entries.map { it.name }.toSet()

        assertEquals(expected, actual)
    }
}
