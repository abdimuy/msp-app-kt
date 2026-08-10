package com.example.msp_app.core.printing.adapters

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks [DantSuPrinterGateway.RESET_PREAMBLE] — the single most fidelity-critical
 * constant in this adapter — against accidental edits. Any change to these bytes
 * must be a deliberate, re-verified decision against the proven msp-app-kt bridge.
 */
class DantSuPrinterGatewayTest {
    @Test
    fun `reset preamble is the exact msp-app-kt bytes in order`() {
        val preamble = DantSuPrinterGateway.RESET_PREAMBLE

        assertEquals(3, preamble.size)
        assertArrayEquals(byteArrayOf(0x1B, 0x40), preamble[0]) // ESC @ — initialise
        assertArrayEquals(byteArrayOf(0x1B, 0x4D, 0x00), preamble[1]) // ESC M 0 — font A
        assertArrayEquals(byteArrayOf(0x1D, 0x21, 0x00), preamble[2]) // GS ! 0 — character size 1x1
    }
}
