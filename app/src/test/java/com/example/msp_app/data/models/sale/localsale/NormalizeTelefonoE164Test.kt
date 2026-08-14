package com.example.msp_app.data.models.sale.localsale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Blindaje del mapper de red (`normalizeTelefonoE164`, usado por `toV2VentaBody`
 * para el campo `cliente.telefono` de `POST /v2/ventas`).
 *
 * Es la última línea de defensa del incidente del 2026-08-13: aunque la pantalla
 * de captura falle o la fila ya venga mal de Room, por el cable nunca debe salir
 * un teléfono que el API vaya a rechazar con `telefono_invalid`. Para el servidor
 * "sin teléfono" es válido en TODOS los tipos de venta, así que `null` siempre
 * entra y basura nunca.
 */
class NormalizeTelefonoE164Test {

    // --- Caso EXACTO del incidente ---

    @Test
    fun `incidente - 000000 jamas produce +52000000`() {
        assertNull(normalizeTelefonoE164("000000"))
    }

    @Test
    fun `incidente - fila vieja de Room con +52000000 sale sin telefono`() {
        // Es la corrección que se aplicó a mano para desatorar la venta de
        // Juan Hernández Cruz: al reintentarse sale sin teléfono y entra, en vez
        // de rebotar para siempre contra `telefono_invalid`.
        assertNull(normalizeTelefonoE164("+52000000"))
    }

    // --- Teléfono ausente ---

    @Test
    fun `telefono vacio produce null`() {
        assertNull(normalizeTelefonoE164(""))
    }

    @Test
    fun `telefono en blanco produce null`() {
        assertNull(normalizeTelefonoE164("   "))
    }

    // --- Teléfono válido ---

    @Test
    fun `los dos formatos de captura normalizan igual`() {
        assertEquals("+522381202772", normalizeTelefonoE164("2381202772"))
        assertEquals("+522381202772", normalizeTelefonoE164("+522381202772"))
    }

    @Test
    fun `recorta espacios de la captura`() {
        assertEquals("+522381202772", normalizeTelefonoE164(" 2381202772 "))
    }

    // --- Longitudes fuera de rango ---

    @Test
    fun `nueve digitos produce null`() {
        assertNull(normalizeTelefonoE164("238120277"))
    }

    @Test
    fun `once digitos produce null`() {
        assertNull(normalizeTelefonoE164("23812027722"))
    }

    @Test
    fun `un + con basura detras ya no pasa intacto`() {
        // El bug original: `if (trimmed.startsWith("+")) return trimmed` dejaba
        // pasar CUALQUIER cosa que empezara con "+", sin contar un solo dígito.
        assertNull(normalizeTelefonoE164("+1"))
        assertNull(normalizeTelefonoE164("+++"))
    }
}
