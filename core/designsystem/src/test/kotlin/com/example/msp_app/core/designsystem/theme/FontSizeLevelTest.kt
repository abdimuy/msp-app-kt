package com.example.msp_app.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM puro: congela los 3 niveles y sus `nominalScale` contra el spec
 * (`docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`
 * §"Decisiones" punto 3) — cualquier cambio aquí desalinea tanto la rampa
 * comprimida ([CompressedTypeRampTest]) como el futuro override de
 * `LocalDensity` en la raíz de composición.
 */
class FontSizeLevelTest {

    @Test
    fun `hay exactamente 3 niveles`() {
        assertEquals(3, FontSizeLevel.entries.size)
    }

    @Test
    fun `NORMAL es 1_0x`() {
        assertEquals(1.0f, FontSizeLevel.NORMAL.nominalScale, 0f)
    }

    @Test
    fun `GRANDE es 1_5x`() {
        assertEquals(1.5f, FontSizeLevel.GRANDE.nominalScale, 0f)
    }

    @Test
    fun `MUY_GRANDE es 2_0x`() {
        assertEquals(2.0f, FontSizeLevel.MUY_GRANDE.nominalScale, 0f)
    }

    @Test
    fun `los niveles son estrictamente crecientes`() {
        val scales = FontSizeLevel.entries.map { it.nominalScale }
        for (i in 1 until scales.size) {
            assert(scales[i] > scales[i - 1]) {
                "esperaba ${scales[i]} > ${scales[i - 1]}"
            }
        }
    }
}
