package com.example.msp_app.feature.garantias.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El GPS del cobrador es una **estimación del teléfono**, no el domicilio del
 * cliente (D5). La puerta [Ubicacion.de] descarta lo inválido en el teléfono en
 * vez de mandarlo para que el servidor lo rechace con 422.
 */
class UbicacionTest {

    @Test
    fun `una coordenada valida se construye`() {
        val ubicacion = Ubicacion(latitud = 19.4326, longitud = -99.1332)

        assertEquals(19.4326, ubicacion.latitud, 0.0)
        assertEquals(-99.1332, ubicacion.longitud, 0.0)
    }

    @Test
    fun `de devuelve la ubicacion cuando el par esta completo`() {
        assertEquals(
            Ubicacion(19.4326, -99.1332),
            Ubicacion.de(19.4326, -99.1332)
        )
    }

    @Test
    fun `de devuelve null cuando falta una coordenada`() {
        assertNull(Ubicacion.de(null, -99.1332))
        assertNull(Ubicacion.de(19.4326, null))
        assertNull(Ubicacion.de(null, null))
    }

    @Test
    fun `de descarta valores fuera de rango`() {
        assertNull(Ubicacion.de(91.0, 0.0))
        assertNull(Ubicacion.de(-91.0, 0.0))
        assertNull(Ubicacion.de(0.0, 181.0))
        assertNull(Ubicacion.de(0.0, -181.0))
    }

    @Test
    fun `de descarta valores que no son numeros`() {
        assertNull(Ubicacion.de(Double.NaN, 0.0))
        assertNull(Ubicacion.de(0.0, Double.POSITIVE_INFINITY))
        assertNull(Ubicacion.de(Double.NEGATIVE_INFINITY, 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `el constructor rechaza una latitud fuera de rango`() {
        Ubicacion(latitud = 90.5, longitud = 0.0)
    }
}
