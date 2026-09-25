package com.example.msp_app.core.geo

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **El control de la aritmética.** Todo lo demás de este módulo se apoya en
 * [Punto.distanciaA]; si mide mal, el radio de 30 m no significa 30 m, el
 * círculo de precisión miente y el cobrador toca la puerta de al lado.
 *
 * Los valores esperados **no salen de correr el código y pegar el resultado**
 * —eso sería una prueba que no puede fallar— sino de la geodesia: un grado de
 * latitud son 111,195 m sobre una esfera de 6,371 km, y un grado de longitud se
 * encoge por el coseno de la latitud.
 */
class PuntoTest {

    @Test
    fun `un grado de latitud son ciento once mil ciento noventa y cinco metros`() {
        val medido = Punto(18.0, -97.0).distanciaA(Punto(19.0, -97.0))
        assertEquals(111_195.0, medido, 5.0)
    }

    @Test
    fun `una milesima de grado de latitud son ciento once metros`() {
        val medido = Punto(18.42, -97.34).distanciaA(Punto(18.421, -97.34))
        assertEquals(111.19, medido, 0.05)
    }

    @Test
    fun `una milesima de longitud encoge por el coseno de la latitud`() {
        // 111.19 * cos(18.42°) = 105.5 m. Si alguien "simplificara" la fórmula a
        // una resta de grados, esta prueba se pone roja: daría 111.19.
        val medido = Punto(18.42, -97.34).distanciaA(Punto(18.42, -97.339))
        assertEquals(105.5, medido, 0.5)
    }

    @Test
    fun `el mismo punto da cero y no NaN`() {
        val p = Punto(18.42318, -97.34106)
        val medido = p.distanciaA(p)
        assertEquals(0.0, medido, 0.0)
        assertFalse("un NaN aquí se propaga al círculo de precisión", medido.isNaN())
    }

    @Test
    fun `puntos casi identicos no producen NaN`() {
        // El caso que motiva el `min(1.0, …)`: sin él, el redondeo de punto
        // flotante puede dejar el radicando apenas arriba de 1 y `asin` devuelve
        // NaN. Se barre una rejilla fina alrededor de un punto real de la ruta.
        val base = Punto(18.4231800, -97.3410600)
        for (i in 0..200) {
            val delta = i * 1e-12
            val medido = base.distanciaA(Punto(base.lat + delta, base.lon + delta))
            assertFalse("NaN con delta=$delta", medido.isNaN())
            assertTrue("distancia negativa con delta=$delta", medido >= 0.0)
        }
    }

    @Test
    fun `la distancia es simetrica`() {
        val a = Punto(18.42318, -97.34106)
        val b = Punto(18.46818, -97.37786)
        assertTrue(abs(a.distanciaA(b) - b.distanciaA(a)) < 1e-9)
    }
}
