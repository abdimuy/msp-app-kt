package com.example.msp_app.core.printing.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the owner's decision: printed tickets never carry accents, because the
 * ESC/POS thermal printers in the field render them as garbage on a limited
 * codepage. [foldToPrintableAscii] is the single pure seam that folds every
 * Spanish diacritic to its plain-ASCII letter before text reaches the printer.
 */
class PrintableAsciiFolderTest {
    @Test
    fun `lowercase vowels with acute accents fold to plain ascii`() {
        assertEquals("aeiou", foldToPrintableAscii("áéíóú"))
    }

    @Test
    fun `uppercase vowels with acute accents fold to plain ascii`() {
        assertEquals("AEIOU", foldToPrintableAscii("ÁÉÍÓÚ"))
    }

    @Test
    fun `u with diaeresis folds to plain u, both cases`() {
        assertEquals("u", foldToPrintableAscii("ü"))
        assertEquals("U", foldToPrintableAscii("Ü"))
    }

    @Test
    fun `enye folds to plain n, both cases`() {
        assertEquals("n", foldToPrintableAscii("ñ"))
        assertEquals("N", foldToPrintableAscii("Ñ"))
    }

    @Test
    fun `plain ascii string is returned unchanged`() {
        val ascii = "RECIBO DE PAGO - Folio 1234 x\$100.00 (50%)"
        assertEquals(ascii, foldToPrintableAscii(ascii))
    }

    @Test
    fun `mixed string folds only the accented characters, leaving width and spacing intact`() {
        val input = "Cliente: José Muñoz - Compró artículos - Dirección: Peñón núm. 5"
        val folded = foldToPrintableAscii(input)

        assertEquals("Cliente: Jose Munoz - Compro articulos - Direccion: Penon num. 5", folded)
        assertEquals(input.length, folded.length) // 1:1 per character — line width is preserved
    }

    @Test
    fun `remaining non-ascii punctuation is dropped rather than printed as garbage`() {
        val folded = foldToPrintableAscii("¿Cuánto? ¡Gracias!")

        assertTrue(folded.all { it.code < 128 })
        assertEquals("Cuanto? Gracias!", folded)
    }

    @Test
    fun `every character of the folded output is ascii`() {
        val input = "MUEBLERÍA BONANZA — Sucursal Peñón — José Núñez — ¿Cómo estás?"

        val folded = foldToPrintableAscii(input)

        assertTrue(folded.all { it.code < 128 })
    }
}
