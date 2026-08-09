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
            type = TelemetryEventType.EVENT,
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
        val a = TelemetryEvent(
            type = TelemetryEventType.TAP,
            name = "boton",
            occurredAt = someInstant,
            props = mapOf("screen" to "inicio")
        )
        val b = TelemetryEvent(
            type = TelemetryEventType.TAP,
            name = "boton",
            occurredAt = someInstant,
            props = mapOf("screen" to "inicio")
        )

        assertEquals(a, b)
    }

    @Test
    fun `all 4 taxonomy types are the base spec set, no more`() {
        val expected = setOf("SCREEN_VIEW", "TAP", "EVENT", "ERROR")
        val actual = TelemetryEventType.entries.map { it.name }.toSet()

        assertEquals(expected, actual)
    }

    // --- props immutability (defensive copy) ---

    @Test
    fun `props is unaffected by mutating the source map after construction`() {
        val source = mutableMapOf("resultado" to "ok")
        val event = TelemetryEvent(
            type = TelemetryEventType.EVENT,
            name = "sync_ok",
            occurredAt = someInstant,
            props = source
        )

        source["resultado"] = "mutado"
        source["nueva_clave"] = "algo"

        assertEquals(mapOf("resultado" to "ok"), event.props)
    }

    @Test
    fun `props rejects mutation attempts even via an unchecked cast to MutableMap`() {
        val event = TelemetryEvent(
            type = TelemetryEventType.EVENT,
            name = "sync_ok",
            occurredAt = someInstant,
            props = mapOf("resultado" to "ok")
        )

        @Suppress("UNCHECKED_CAST")
        val mutableView = event.props as MutableMap<String, String>

        assertThrows(UnsupportedOperationException::class.java) {
            mutableView["resultado"] = "hackeado"
        }
    }

    // --- screen validation on TAP, same invariant as screenView's name ---

    @Test
    fun `TAP requires a screen prop`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEvent(
                type = TelemetryEventType.TAP,
                name = "boton_confirmar",
                occurredAt = someInstant
            )
        }
    }

    @Test
    fun `TAP rejects a screen that is not a static identifier, same rule as screenView's name`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEvent(
                type = TelemetryEventType.TAP,
                name = "boton_confirmar",
                occurredAt = someInstant,
                props = mapOf("screen" to "Pantalla De Cobranza")
            )
        }
    }

    @Test
    fun `TAP accepts a screen that is a valid static identifier`() {
        val event = TelemetryEvent(
            type = TelemetryEventType.TAP,
            name = "boton_confirmar",
            occurredAt = someInstant,
            props = mapOf("screen" to "cobranza_detalle")
        )

        assertEquals("cobranza_detalle", event.props["screen"])
    }

    @Test
    fun `EVENT and ERROR do not require a screen prop`() {
        val event =
            TelemetryEvent(
                type = TelemetryEventType.EVENT,
                name = "sync_ok",
                occurredAt = someInstant
            )
        val error =
            TelemetryEvent(
                type = TelemetryEventType.ERROR,
                name = "red_timeout",
                occurredAt = someInstant
            )

        assertTrue(event.props.isEmpty())
        assertTrue(error.props.isEmpty())
    }
}
