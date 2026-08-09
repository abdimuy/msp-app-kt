package com.example.msp_app.core.telemetry.queue

import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extiende [RobolectricTestBase] únicamente porque [TelemetryPropsCodec.decode]
 * loguea vía `android.util.Log` en el camino de error (JSON corrupto) — sin
 * Robolectric, `Log.w` revienta con "not mocked" en JVM plano.
 */
class TelemetryPropsCodecTest : RobolectricTestBase() {

    @Test
    fun `encode-decode es un round-trip exacto para props no vacios`() {
        val props = mapOf("resultado" to "ok", "intentos" to "3", "screen" to "cobranza_detalle")

        val json = TelemetryPropsCodec.encode(props)
        val decoded = TelemetryPropsCodec.decode(json)

        assertEquals(props, decoded)
    }

    @Test
    fun `encode de un mapa vacio produce un objeto JSON vacio`() {
        assertEquals("{}", TelemetryPropsCodec.encode(emptyMap()))
    }

    @Test
    fun `decode de un JSON vacio retorna un mapa vacio`() {
        assertTrue(TelemetryPropsCodec.decode("{}").isEmpty())
    }

    @Test
    fun `decode de un string en blanco retorna un mapa vacio sin lanzar`() {
        assertTrue(TelemetryPropsCodec.decode("").isEmpty())
        assertTrue(TelemetryPropsCodec.decode("   ").isEmpty())
    }

    @Test
    fun `decode de JSON corrupto no lanza, retorna mapa vacio`() {
        val decoded = TelemetryPropsCodec.decode("{esto no es json valido")

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decode preserva valores con caracteres especiales sin corromperlos`() {
        val props = mapOf("mensaje" to "timeout tras 3 intentos: 500", "codigo" to "err_5xx")

        val decoded = TelemetryPropsCodec.decode(TelemetryPropsCodec.encode(props))

        assertEquals(props, decoded)
    }
}
