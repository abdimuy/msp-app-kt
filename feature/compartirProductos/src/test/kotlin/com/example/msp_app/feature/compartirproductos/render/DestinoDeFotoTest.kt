package com.example.msp_app.feature.compartirproductos.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** La foto se ve SIEMPRE completa: cabe entera, centrada, sin recorte y sin deformarse. */
class DestinoDeFotoTest {

    private val cajaAncho = 1080
    private val cajaAlto = 1008

    private fun caber(w: Int, h: Int) = DestinoDeFoto.caber(w, h, cajaAncho, cajaAlto)

    /** Lo que debe cumplir CUALQUIER foto: dentro del recuadro, centrada, tocando un borde y sin deformar. */
    private fun verificar(nombre: String, w: Int, h: Int, d: DestinoDeFoto) {
        assertTrue(
            "$nombre: dentro",
            d.izquierda >= 0 && d.arriba >= 0 && d.derecha <= cajaAncho && d.abajo <= cajaAlto
        )
        assertTrue("$nombre: ancho y alto positivos", d.ancho >= 1 && d.alto >= 1)
        assertTrue(
            "$nombre: centrada en x (${d.izquierda}, ${cajaAncho - d.derecha})",
            Math.abs(d.izquierda - (cajaAncho - d.derecha)) <= 1
        )
        assertTrue(
            "$nombre: centrada en y (${d.arriba}, ${cajaAlto - d.abajo})",
            Math.abs(d.arriba - (cajaAlto - d.abajo)) <= 1
        )
        assertTrue("$nombre: toca un borde", d.ancho == cajaAncho || d.alto == cajaAlto)
        // Sin deformar: la proporción de lo dibujado es la de la foto (con ±1 px de redondeo).
        val esperadoAlto = d.ancho.toDouble() * h / w
        val esperadoAncho = d.alto.toDouble() * w / h
        assertTrue(
            "$nombre: proporción",
            Math.abs(d.alto - esperadoAlto) <= 1.0 || Math.abs(d.ancho - esperadoAncho) <= 1.0
        )
    }

    @Test fun `foto muy alta 336 por 900 cabe por el alto y deja aire a los lados`() {
        val d = caber(336, 900)
        verificar("336×900", 336, 900, d)
        assertEquals(cajaAlto, d.alto)
        assertEquals(0, d.arriba)
        assertEquals(376, d.ancho) // 336 × 1008 / 900
        assertEquals(352, d.izquierda)
    }

    @Test fun `foto muy ancha 900 por 302 cabe por el ancho y deja aire arriba y abajo`() {
        val d = caber(900, 302)
        verificar("900×302", 900, 302, d)
        assertEquals(cajaAncho, d.ancho)
        assertEquals(0, d.izquierda)
        assertEquals(362, d.alto) // 302 × 1080 / 900
        assertEquals(323, d.arriba)
    }

    @Test fun `foto de proporcion exacta llena el recuadro`() {
        val d = caber(1080, 1008)
        verificar("1080×1008", 1080, 1008, d)
        assertEquals(DestinoDeFoto(0, 0, 1080, 1008), d)
        assertEquals(DestinoDeFoto(0, 0, 1080, 1008), caber(2160, 2016))
        assertEquals(DestinoDeFoto(0, 0, 1080, 1008), caber(540, 504))
    }

    @Test fun `foto chica 200 por 150 se agranda hasta caber y no queda diminuta`() {
        val d = caber(200, 150)
        verificar("200×150", 200, 150, d)
        assertEquals(cajaAncho, d.ancho) // 200×150 es más ancha que el recuadro: limita el ancho
        assertEquals(810, d.alto) // 150 × 1080 / 200
        assertTrue("se agrandó", d.ancho > 200 && d.alto > 150)
    }

    @Test fun `foto girada por EXIF se mide ya girada`() {
        // Una 800×400 con orientación 6 llega a la tarjeta como 400×800.
        val d = caber(400, 800)
        verificar("400×800 (girada)", 400, 800, d)
        assertEquals(cajaAlto, d.alto)
        assertEquals(504, d.ancho)
    }

    @Test fun `un conjunto de formas cumple todo a la vez, tambien con otros alto de recuadro`() {
        listOf(1 to 1, 10 to 10000, 10000 to 10, 4000 to 3000, 3000 to 4000, 1080 to 1920, 1920 to 1080, 336 to 900, 900 to 302)
            .forEach { (w, h) ->
                listOf(1008, 768, 900).forEach { alto ->
                    val d = DestinoDeFoto.caber(w, h, cajaAncho, alto)
                    assertTrue(
                        "$w×$h/$alto dentro",
                        d.izquierda >= 0 && d.arriba >= 0 && d.derecha <= cajaAncho && d.abajo <= alto
                    )
                    assertTrue("$w×$h/$alto toca borde", d.ancho == cajaAncho || d.alto == alto)
                    assertTrue(
                        "$w×$h/$alto centrada",
                        Math.abs(d.izquierda - (cajaAncho - d.derecha)) <= 1 && Math.abs(d.arriba - (alto - d.abajo)) <= 1
                    )
                }
            }
    }

    @Test fun `medidas invalidas se rechazan`() {
        assertThrows(IllegalArgumentException::class.java) { DestinoDeFoto.caber(0, 10, 10, 10) }
        assertThrows(IllegalArgumentException::class.java) { DestinoDeFoto.caber(10, 10, 10, 0) }
    }
}
