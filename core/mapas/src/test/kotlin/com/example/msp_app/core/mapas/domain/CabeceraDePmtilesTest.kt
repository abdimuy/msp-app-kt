package com.example.msp_app.core.mapas.domain

import com.example.msp_app.core.mapas.fake.ExtractoReal
import com.example.msp_app.core.mapas.fake.cabeceraDePruebaDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * El lector de la cabecera de PMTiles v3.
 *
 * ## El control positivo está en el archivo de verdad
 *
 * Un parser con los desplazamientos corridos pasaría todos los tests
 * sintéticos de abajo —los escribe el mismo código que los lee—. Lo que prueba
 * que los desplazamientos son los del formato y no los que yo creí, es leer el
 * extracto **real** y comparar contra los números que
 * `huella-geografica-medida.md` midió por un camino completamente distinto
 * (SQL contra Firebird, y el CLI de `pmtiles`). Que los dos den `-98.2, 17.9 →
 * -96.4, 19.6` y zoom 0–14 no puede ser casualidad.
 */
class CabeceraDePmtilesTest {

    @Test
    fun `lee la version, el zoom y la caja`() {
        val cabecera = leerCabeceraDePmtiles(cabeceraDePruebaDe(finDeLosDatos = 999L))
            .getOrThrow()

        assertEquals(3, cabecera.version)
        assertEquals(ExtractoReal.ZOOM_MAXIMO_MEDIDO, cabecera.zoomMaximo)
        assertEquals(ExtractoReal.OESTE_MEDIDO, cabecera.oeste, TOLERANCIA)
        assertEquals(ExtractoReal.NORTE_MEDIDO, cabecera.norte, TOLERANCIA)
        assertEquals(999L, cabecera.finDeLosDatos)
    }

    @Test
    fun `un archivo mas corto que la cabecera no se lee`() {
        val fallo = leerCabeceraDePmtiles(ByteArray(LARGO_DE_LA_CABECERA - 1)).exceptionOrNull()

        assertEquals(FalloDeLaCabecera.DEMASIADO_CORTO, (fallo as CabeceraInvalida).motivo)
    }

    @Test
    fun `sin la magia no es un pmtiles`() {
        val bytes = cabeceraDePruebaDe(finDeLosDatos = 999L, magia = "PNGXXXX")

        val fallo = leerCabeceraDePmtiles(bytes).exceptionOrNull()

        assertEquals(FalloDeLaCabecera.SIN_LA_MAGIA, (fallo as CabeceraInvalida).motivo)
    }

    /**
     * PMTiles v2 existe y MapLibre no lo lee. Aceptarlo dejaría que el motor
     * abriera un archivo que no entiende y dibujara nada, sin decir por qué.
     */
    @Test
    fun `una version que MapLibre no lee se rechaza`() {
        val bytes = cabeceraDePruebaDe(finDeLosDatos = 999L, version = 2)

        val fallo = leerCabeceraDePmtiles(bytes).exceptionOrNull()

        assertEquals(FalloDeLaCabecera.VERSION_AJENA, (fallo as CabeceraInvalida).motivo)
    }

    // ── Control positivo: el extracto de verdad ─────────────────────────────

    @Test
    fun `el extracto real dice exactamente lo que la huella midio`() {
        assumeTrue("no esta el extracto real: ${ExtractoReal.archivo}", ExtractoReal.disponible)
        val bytes = ByteArray(LARGO_DE_LA_CABECERA)
        ExtractoReal.archivo.inputStream().use { it.read(bytes) }

        val cabecera = leerCabeceraDePmtiles(bytes).getOrThrow()

        assertEquals(3, cabecera.version)
        assertEquals(0, cabecera.zoomMinimo)
        assertEquals(ExtractoReal.ZOOM_MAXIMO_MEDIDO, cabecera.zoomMaximo)
        assertEquals(ExtractoReal.OESTE_MEDIDO, cabecera.oeste, TOLERANCIA)
        assertEquals(ExtractoReal.SUR_MEDIDO, cabecera.sur, TOLERANCIA)
        assertEquals(ExtractoReal.ESTE_MEDIDO, cabecera.este, TOLERANCIA)
        assertEquals(ExtractoReal.NORTE_MEDIDO, cabecera.norte, TOLERANCIA)
    }

    /**
     * La prueba de completitud, sobre el archivo real: lo que la cabecera dice
     * que mide **es** lo que mide. Es la línea entera de la que depende que un
     * `.pmtiles` a medias no llegue nunca a MapLibre.
     */
    @Test
    fun `el extracto real termina donde su cabecera dice`() {
        assumeTrue("no esta el extracto real: ${ExtractoReal.archivo}", ExtractoReal.disponible)
        val bytes = ByteArray(LARGO_DE_LA_CABECERA)
        ExtractoReal.archivo.inputStream().use { it.read(bytes) }

        val cabecera = leerCabeceraDePmtiles(bytes).getOrThrow()

        assertEquals(ExtractoReal.archivo.length(), cabecera.finDeLosDatos)
        assertEquals(ExtractoReal.TAMANO_MEDIDO, ExtractoReal.archivo.length())
    }

    /** La caja del extracto real contiene Tehuacán, que es donde se cobra. */
    @Test
    fun `el extracto real cubre Tehuacan`() {
        assumeTrue("no esta el extracto real: ${ExtractoReal.archivo}", ExtractoReal.disponible)
        val bytes = ByteArray(LARGO_DE_LA_CABECERA)
        ExtractoReal.archivo.inputStream().use { it.read(bytes) }
        val mapa =
            MapaDeLaRuta(
                ExtractoReal.archivo.absolutePath,
                leerCabeceraDePmtiles(bytes).getOrThrow()
            )

        assertTrue(mapa.cubre(PuntoDelMapa(lat = 18.4609, lng = -97.3926)))
        // Mountain View: las 65 visitas del emulador que la huella encontró.
        // Están fuera de la caja, y el mapa tiene que saber decirlo.
        assertTrue(!mapa.cubre(PuntoDelMapa(lat = 37.4220, lng = -122.0841)))
    }

    private companion object {
        const val TOLERANCIA = 1e-6
    }
}
