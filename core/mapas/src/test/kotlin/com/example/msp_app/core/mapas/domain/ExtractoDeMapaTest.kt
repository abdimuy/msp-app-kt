package com.example.msp_app.core.mapas.domain

import com.example.msp_app.core.mapas.fake.ExtractoReal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** El paquete anunciado y cómo se cuentan sus megas. */
class ExtractoDeMapaTest {

    /**
     * **25.5 y no 24.** `huella-geografica-medida.md` dice "24 MB" y los dos
     * números son el mismo archivo: 24 son mebibytes (2²⁰) y 25.5 son megabytes
     * decimales (10⁶). La app usa decimales porque es lo que `:core:speech` y
     * `:core:appgate` ya dicen, y decir dos unidades distintas en dos pantallas
     * de descarga sería peor que decir la que sea.
     */
    @Test
    fun `el extracto real se anuncia como 25 punto 5 MB`() {
        assertEquals("25.5", megas(ExtractoReal.TAMANO_MEDIDO))
    }

    @Test
    fun `una mega redonda no lleva decimal`() {
        assertEquals("25", megas(25_000_000L))
    }

    @Test
    fun `la fraccion se recorta a uno`() {
        val avance = AvanceDeLaDescarga(bytesBajados = 30_000_000L, bytesTotales = 25_000_000L)

        assertEquals(1f, avance.fraccion)
    }

    @Test
    fun `sin total no hay barra`() {
        assertEquals(0f, AvanceDeLaDescarga(bytesBajados = 10L, bytesTotales = 0L).fraccion)
    }

    @Test
    fun `el texto de avance dice los dos numeros`() {
        val avance = AvanceDeLaDescarga(bytesBajados = 12_000_000L, bytesTotales = 25_507_515L)

        assertEquals("12 de 25.5 MB", avance.enMegas)
    }

    @Test
    fun `un extracto sin url no se construye`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtractoDeMapa(url = "  ", tamanoBytes = 1L)
        }
    }

    /** Media coordenada no ubica nada, y una fuera del mundo tampoco. */
    @Test
    fun `un punto fuera del mundo no se construye`() {
        assertThrows(IllegalArgumentException::class.java) {
            PuntoDelMapa(lat = 91.0, lng = 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PuntoDelMapa(lat = 0.0, lng = -181.0)
        }
    }
}
