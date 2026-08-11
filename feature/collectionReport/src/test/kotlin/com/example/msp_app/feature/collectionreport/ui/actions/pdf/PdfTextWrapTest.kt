package com.example.msp_app.feature.collectionreport.ui.actions.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura de [PdfTextWrap] — el helper de envoltura de texto de la nota completa de una
 * visita en el PDF (rediseño: tabla densa multipágina, ver KDoc de
 * [com.example.msp_app.feature.collectionreport.ui.actions.ReportActionsController.generatePdf]).
 * Pura (medidor inyectado, ver KDoc del archivo) — [FAKE_MEASURE] simula un ancho lineal de 5pt
 * por carácter, determinista y suficiente para verificar los cortes sin un `Paint` real.
 */
class PdfTextWrapTest {

    private val charWidth = 5f
    private val fakeMeasure: (String) -> Float = { it.length * charWidth }

    @Test
    fun `texto en blanco no produce lineas`() {
        assertEquals(emptyList<String>(), PdfTextWrap.wrap("", 100f, fakeMeasure))
        assertEquals(emptyList<String>(), PdfTextWrap.wrap("   ", 100f, fakeMeasure))
    }

    @Test
    fun `texto que cabe entero produce una sola linea`() {
        val lines = PdfTextWrap.wrap("Cliente inconforme", 200f, fakeMeasure)
        assertEquals(listOf("Cliente inconforme"), lines)
    }

    @Test
    fun `texto largo se envuelve en varias lineas sin perder ninguna palabra`() {
        val nota = "No estaba en su domicilio, dejé recado con la vecina para que avise"
        // maxWidth = 20 chars ~ (100pt / 5pt por char).
        val lines = PdfTextWrap.wrap(nota, 100f, fakeMeasure)

        assertTrue("debe partirse en más de una línea", lines.size > 1)
        lines.forEach { line ->
            assertTrue(
                "línea excede el ancho: '$line'",
                fakeMeasure(line) <= 100f
            )
        }
        // Ninguna palabra se pierde ni se trunca: unir las líneas reconstruye el texto original.
        assertEquals(nota, lines.joinToString(" "))
    }

    @Test
    fun `una palabra mas ancha que el maximo se corta en el prefijo mas largo que cabe, sin perder caracteres`() {
        val word = "Constantinopolitanamente" // 25 chars, no cabe en 40pt (8 chars) de una vez.
        val lines = PdfTextWrap.wrap(word, 40f, fakeMeasure)

        assertTrue("debe partirse en más de una línea", lines.size > 1)
        assertEquals(word, lines.joinToString(""))
        lines.dropLast(1).forEach { line -> assertTrue(fakeMeasure(line) <= 40f) }
    }

    @Test
    fun `respeta espacios multiples como separadores simples sin duplicar palabras`() {
        val lines = PdfTextWrap.wrap("Pidió   que regrese", 300f, fakeMeasure)
        assertEquals(listOf("Pidió que regrese"), lines)
    }
}
