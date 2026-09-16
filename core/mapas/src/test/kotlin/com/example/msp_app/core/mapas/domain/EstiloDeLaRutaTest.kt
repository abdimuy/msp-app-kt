package com.example.msp_app.core.mapas.domain

import com.example.msp_app.core.mapas.fake.ExtractoReal
import com.example.msp_app.core.mapas.fake.mapaDePrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El estilo del mapa.
 *
 * Lo que se mide acá no es estético: son las cuatro propiedades de las que
 * depende que el mapa funcione **sin llave, sin servidor y sin señal**.
 */
class EstiloDeLaRutaTest {

    private val paleta = PaletaDelMapa(
        tierra = "#FBFCFC",
        suelo = "#E4F1E9",
        agua = "#E9EEF8",
        edificios = "#E4E8E6",
        calles = "#5C6863"
    )

    @Test
    fun `la fuente apunta al pmtiles local con el esquema que MapLibre entiende`() {
        val estilo = estiloDeLaRuta(mapaDePrueba("/datos/mapas/ruta.pmtiles"), paleta)

        assertTrue(estilo.contains(""""url": "pmtiles://file:///datos/mapas/ruta.pmtiles""""))
    }

    /**
     * **La propiedad entera del módulo, en una sola aserción.**
     *
     * `glyphs` y `sprite` son URLs a servidores de tipografías e iconos. Con
     * cualquiera de las dos, el mapa dejaría de funcionar sin señal —y la
     * pantalla de descarga estaría prometiendo algo falso—. Por eso el estilo no
     * tiene capas de texto: no es que no se hayan hecho, es que no pueden
     * existir sin romper esto.
     */
    @Test
    fun `el estilo no pide NADA a la red`() {
        val estilo = estiloDeLaRuta(mapaDePrueba(), paleta)

        assertFalse("un `glyphs` mata el «funciona sin señal»", estilo.contains("glyphs"))
        assertFalse("un `sprite` mata el «funciona sin señal»", estilo.contains("sprite"))
        assertFalse(estilo.contains("http://"))
        // La única `https` que podría aparecer sería la de un servidor de
        // teselas. La atribución se pinta en Compose, sin enlace.
        assertFalse(estilo.contains("https://"))
    }

    /** El zoom máximo sale del archivo, no de una constante. */
    @Test
    fun `el zoom de la fuente lo pone la cabecera`() {
        val estilo = estiloDeLaRuta(mapaDePrueba(), paleta)

        assertTrue(estilo.contains(""""maxzoom": ${ExtractoReal.ZOOM_MAXIMO_MEDIDO}"""))
    }

    @Test
    fun `pinta las cinco capas que el extracto declara`() {
        val estilo = estiloDeLaRuta(mapaDePrueba(), paleta)

        listOf("earth", "landuse", "water", "buildings", "roads").forEach {
            assertTrue("falta la capa $it", estilo.contains(""""source-layer":"$it""""))
        }
    }

    /**
     * La ruta del archivo viene del sistema de archivos del teléfono. Una
     * comilla ahí adentro rompería el JSON entero y MapLibre se quedaría en
     * blanco sin decir por qué.
     */
    @Test
    fun `una ruta con comillas no rompe el JSON`() {
        val estilo = estiloDeLaRuta(mapaDePrueba("""/datos/a"b\c/ruta.pmtiles"""), paleta)

        assertTrue(estilo.contains("""pmtiles://file:///datos/a\"b\\c/ruta.pmtiles"""))
    }

    /** La atribución viaja también dentro del estilo, donde la especificación la espera. */
    @Test
    fun `el estilo declara la atribucion de OpenStreetMap`() {
        val estilo = estiloDeLaRuta(mapaDePrueba(), paleta)

        assertTrue(estilo.contains(ATRIBUCION_DE_OSM))
    }

    @Test
    fun `los colores son los que le pasan, sin paleta propia`() {
        val estilo = estiloDeLaRuta(mapaDePrueba(), paleta)

        assertEquals(2, Regex(Regex.escape(paleta.tierra)).findAll(estilo).count())
        assertTrue(estilo.contains(paleta.agua))
        assertTrue(estilo.contains(paleta.calles))
    }
}
