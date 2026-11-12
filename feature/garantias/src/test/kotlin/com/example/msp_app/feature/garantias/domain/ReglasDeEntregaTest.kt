package com.example.msp_app.feature.garantias.domain

import com.example.msp_app.feature.garantias.domain.model.FirmaDeConformidad
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La entrega sin GPS no se encola, y la firma de más de 1 MB no se acepta**
 * (prueba 6 del brief, mutante A8).
 *
 * Los bordes se prueban exactos: 1 MB pasa, 1 MB + 1 byte no. El servidor usa
 * el mismo tope (`maxFirmaBytes = 1 << 20`), así que un byte de más es un
 * comando que muere en 422 y no se puede corregir desde el teléfono.
 */
class ReglasDeEntregaTest {

    private val ubicacion = Ubicacion(latitud = 19.4326, longitud = -99.1332)
    private val firmaValida = FirmaDeConformidad(
        rutaArchivo = "/files/firma.png",
        tamanoBytes = 1024
    )

    @Test
    fun `con ubicacion y firma se puede entregar`() {
        assertTrue(ReglasDeEntrega.sePuedeEntregar(ubicacion, firmaValida))
        assertEquals(
            emptyList<BloqueoDeEntrega>(),
            ReglasDeEntrega.bloqueosDe(ubicacion, firmaValida)
        )
    }

    @Test
    fun `sin ubicacion no se puede entregar`() {
        assertEquals(
            listOf(BloqueoDeEntrega.SIN_UBICACION),
            ReglasDeEntrega.bloqueosDe(null, firmaValida)
        )
    }

    @Test
    fun `sin firma no se puede entregar`() {
        assertEquals(
            listOf(BloqueoDeEntrega.SIN_FIRMA),
            ReglasDeEntrega.bloqueosDe(ubicacion, null)
        )
    }

    @Test
    fun `sin ubicacion y sin firma se reportan los dos bloqueos`() {
        assertEquals(
            listOf(BloqueoDeEntrega.SIN_UBICACION, BloqueoDeEntrega.SIN_FIRMA),
            ReglasDeEntrega.bloqueosDe(null, null)
        )
    }

    @Test
    fun `una firma vacia no se acepta`() {
        val firma = firmaValida.copy(tamanoBytes = 0)
        assertEquals(
            listOf(BloqueoDeEntrega.FIRMA_VACIA),
            ReglasDeEntrega.bloqueosDe(ubicacion, firma)
        )
    }

    @Test
    fun `una firma de mas de un mebibyte no se acepta`() {
        val firma = firmaValida.copy(tamanoBytes = ReglasDeEntrega.MAX_BYTES_DE_FIRMA + 1)
        assertEquals(
            listOf(BloqueoDeEntrega.FIRMA_MUY_GRANDE),
            ReglasDeEntrega.bloqueosDe(ubicacion, firma)
        )
    }

    @Test
    fun `una firma de exactamente un mebibyte si se acepta`() {
        val firma = firmaValida.copy(tamanoBytes = ReglasDeEntrega.MAX_BYTES_DE_FIRMA)
        assertTrue(ReglasDeEntrega.sePuedeEntregar(ubicacion, firma))
    }

    @Test
    fun `los bytes de la firma respetan el mismo tope`() {
        assertFalse(ReglasDeEntrega.firmaValida(ByteArray(0)))
        assertTrue(ReglasDeEntrega.firmaValida(ByteArray(ReglasDeEntrega.MAX_BYTES_DE_FIRMA)))
        assertFalse(ReglasDeEntrega.firmaValida(ByteArray(ReglasDeEntrega.MAX_BYTES_DE_FIRMA + 1)))
    }

    @Test
    fun `el tipo de la firma es fijo y no se deduce del archivo`() {
        assertEquals("image/png", ReglasDeEntrega.MIME_DE_LA_FIRMA)
        assertEquals(ReglasDeEntrega.MIME_DE_LA_FIRMA, firmaValida.mime)
    }
}
