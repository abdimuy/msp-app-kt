package com.example.msp_app.core.appgate.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El progreso se muestra en **megas**, con el formato exacto del mockup
 * aprobado ("4.2 de 11 MB"). Una rueda indeterminada es la que termina en una
 * llamada por teléfono, así que estos textos son parte del contrato.
 */
class DownloadProgressTest {

    @Test
    fun `formatea con un decimal cuando no es redondo`() {
        assertEquals("4.2", formatMegabytes(4_200_000L))
    }

    @Test
    fun `formatea sin decimal cuando es redondo`() {
        assertEquals("11", formatMegabytes(11_000_000L))
    }

    @Test
    fun `cero megas se lee cero`() {
        assertEquals("0", formatMegabytes(0L))
    }

    @Test
    fun `el punto decimal no depende del idioma del telefono`() {
        // `Locale.US` fijo: con la configuración regional de México el
        // `String.format` por defecto imprimiría "4,2".
        assertTrue(formatMegabytes(6_100_000L).contains('.'))
    }

    @Test
    fun `la etiqueta bajo la barra es 'bajado de total MB'`() {
        val progress = DownloadProgress(downloadedBytes = 4_200_000L, totalBytes = 11_000_000L)

        assertEquals("4.2 de 11 MB", progress.megabytesLabel())
    }

    @Test
    fun `el boton de datos moviles anuncia el peso`() {
        assertEquals("11 MB", megabytesLabel(11_000_000L))
    }

    @Test
    fun `la fraccion y el porcentaje salen del avance real`() {
        val progress = DownloadProgress(downloadedBytes = 4_180_000L, totalBytes = 11_000_000L)

        assertEquals(0.38f, progress.fraction, 0.005f)
        assertEquals(38, progress.percent)
    }

    @Test
    fun `sin total conocido no se inventa una fraccion`() {
        val progress = DownloadProgress(downloadedBytes = 4_200_000L, totalBytes = 0L)

        assertEquals(0f, progress.fraction, 0f)
        assertFalse(progress.complete)
    }

    @Test
    fun `un avance mayor que el total no se sale de la barra`() {
        val progress = DownloadProgress(downloadedBytes = 12_000_000L, totalBytes = 11_000_000L)

        assertEquals(1f, progress.fraction, 0f)
        assertTrue(progress.complete)
    }
}
