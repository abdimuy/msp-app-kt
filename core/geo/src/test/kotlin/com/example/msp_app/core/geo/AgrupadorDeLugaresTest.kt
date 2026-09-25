package com.example.msp_app.core.geo

import com.example.msp_app.core.geo.LugaresFixtures.PUERTA
import com.example.msp_app.core.geo.LugaresFixtures.desplazado
import com.example.msp_app.core.geo.LugaresFixtures.medicion
import com.example.msp_app.core.geo.LugaresFixtures.sinPuntosCompartidos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El radio de 30 m y lo que se sigue de él.
 *
 * Cada prueba de aquí se pone **roja** si alguien cambia el radio sin cambiar la
 * tabla del KDoc de [AgrupadorDeLugares] — que es exactamente lo que se quiere:
 * el número está medido y moverlo es una decisión, no un ajuste.
 */
class AgrupadorDeLugaresTest {

    @Test
    fun `dos mediciones a veinte metros son el mismo lugar`() {
        val lugares = AgrupadorDeLugares.agrupar(
            listOf(
                medicion(PUERTA),
                medicion(desplazado(PUERTA, metrosNorte = 20.0), diasAtras = 7)
            ),
            sinPuntosCompartidos()
        )
        assertEquals(1, lugares.size)
        assertEquals(2, lugares.single().conteo)
    }

    @Test
    fun `dos mediciones a cien metros son lugares distintos`() {
        val lugares = AgrupadorDeLugares.agrupar(
            listOf(
                medicion(PUERTA),
                medicion(desplazado(PUERTA, metrosNorte = 100.0), diasAtras = 7)
            ),
            sinPuntosCompartidos()
        )
        assertEquals(2, lugares.size)
    }

    @Test
    fun `el radio corta en treinta metros, ni mas ni menos`() {
        // **Las distancias van LITERALES, no leídas de la constante.** Una
        // primera versión de esta prueba usaba
        // `RADIO_DE_AGRUPACION_M ± 1` y por eso NO podía fallar: al mutar la
        // constante a 60 la prueba medía 59 y 61 y seguía verde. Se verificó
        // el 2026-09-24 mutando el valor y viendo la suite pasar — un falso
        // verde de manual.
        //
        // Con 29 y 31 metros duros, cualquier radio que no sea 30 rompe una de
        // las dos afirmaciones. Si alguien cambia el número a conciencia, tiene
        // que venir aquí, y de paso a la tabla del KDoc de AgrupadorDeLugares.
        fun gruposA(metros: Double) = AgrupadorDeLugares.agrupar(
            listOf(medicion(PUERTA), medicion(desplazado(PUERTA, metros))),
            sinPuntosCompartidos()
        ).size
        assertEquals("a 29 m tienen que ser el mismo lugar", 1, gruposA(29.0))
        assertEquals("a 31 m tienen que ser dos lugares", 2, gruposA(31.0))
    }

    @Test
    fun `la cadena del enlace simple une lo que esta encadenado`() {
        // Tres mediciones en fila, cada una a 25 m de la siguiente: los extremos
        // están a 50 m —más que el radio— y aun así son el mismo lugar, porque
        // hay camino. Es la propiedad del enlace simple y es deliberada: una
        // banqueta larga es un solo domicilio.
        val lugares = AgrupadorDeLugares.agrupar(
            listOf(
                medicion(PUERTA),
                medicion(desplazado(PUERTA, 25.0)),
                medicion(desplazado(PUERTA, 50.0))
            ),
            sinPuntosCompartidos()
        )
        assertEquals(1, lugares.size)
    }

    @Test
    fun `los lugares salen del mayor al menor`() {
        val lugares = AgrupadorDeLugares.agrupar(
            listOf(
                medicion(desplazado(PUERTA, 500.0), id = "lejano"),
                medicion(PUERTA, id = "a"),
                medicion(desplazado(PUERTA, 5.0), id = "b"),
                medicion(desplazado(PUERTA, 10.0), id = "c")
            ),
            sinPuntosCompartidos()
        )
        assertEquals(listOf(3, 1), lugares.map { it.conteo })
    }

    @Test
    fun `una transferencia lejana no jala el centro del lugar`() {
        // La transferencia entra al mismo grupo (está dentro del radio) pero el
        // centro se promedia sin ella. Si alguien quita ese filtro, el pin se
        // corre hacia donde el cobrador estaba parado.
        val conTransferencia = AgrupadorDeLugares.agrupar(
            listOf(
                medicion(PUERTA),
                medicion(desplazado(PUERTA, 2.0)),
                medicion(desplazado(PUERTA, 28.0), esTransferencia = true)
            ),
            sinPuntosCompartidos()
        ).single()
        val sinTransferencia = AgrupadorDeLugares.agrupar(
            listOf(medicion(PUERTA), medicion(desplazado(PUERTA, 2.0))),
            sinPuntosCompartidos()
        ).single()
        assertEquals(3, conTransferencia.conteo)
        assertEquals(2, conTransferencia.conteoDeDomicilio)
        assertTrue(
            "el centro se movió por una transferencia",
            conTransferencia.centro.distanciaA(sinTransferencia.centro) < 0.5
        )
    }

    @Test
    fun `sin mediciones no hay lugares`() {
        assertTrue(AgrupadorDeLugares.agrupar(emptyList(), sinPuntosCompartidos()).isEmpty())
    }

    @Test
    fun `el circulo de precision no se dibuja con menos de cinco mediciones`() {
        val cuatro = AgrupadorDeLugares.agrupar(
            (0 until 4).map { medicion(desplazado(PUERTA, it * 3.0), diasAtras = it.toLong()) },
            sinPuntosCompartidos()
        ).single()
        val cinco = AgrupadorDeLugares.agrupar(
            (0 until 5).map { medicion(desplazado(PUERTA, it * 3.0), diasAtras = it.toLong()) },
            sinPuntosCompartidos()
        ).single()
        assertNull("con cuatro mediciones la dispersión es ruido", cuatro.radioDeConfianzaM)
        assertNotNull(cinco.radioDeConfianzaM)
    }

    @Test
    fun `el circulo no se calcula sobre transferencias`() {
        // Cuatro mediciones de domicilio muy juntas y una transferencia a 25 m.
        // Si el círculo contara la transferencia habría cinco puntos y saldría
        // ancho; como no cuenta, quedan cuatro y no se dibuja.
        val lugar = AgrupadorDeLugares.agrupar(
            (0 until 4).map { medicion(desplazado(PUERTA, it * 1.0), diasAtras = it.toLong()) } +
                medicion(desplazado(PUERTA, 25.0), diasAtras = 9, esTransferencia = true),
            sinPuntosCompartidos()
        ).single()
        assertEquals(5, lugar.conteo)
        assertNull(lugar.radioDeConfianzaM)
    }

    @Test
    fun `un lugar apretado da un circulo mas chico que uno disperso`() {
        fun radioDe(paso: Double) = AgrupadorDeLugares.agrupar(
            (0 until 8).map { medicion(desplazado(PUERTA, it * paso), diasAtras = it.toLong()) },
            sinPuntosCompartidos()
        ).first().radioDeConfianzaM!!
        assertTrue(
            "el círculo no refleja la dispersión real",
            radioDe(1.0) < radioDe(3.0)
        )
    }
}
