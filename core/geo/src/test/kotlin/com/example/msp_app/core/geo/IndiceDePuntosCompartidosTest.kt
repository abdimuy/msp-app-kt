package com.example.msp_app.core.geo

import com.example.msp_app.core.geo.LugaresFixtures.PUERTA
import com.example.msp_app.core.geo.LugaresFixtures.PUNTO_MUY_COMPARTIDO
import com.example.msp_app.core.geo.LugaresFixtures.desplazado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndiceDePuntosCompartidosTest {

    private fun puntos(vararg p: Triple<Int, String, Punto>) =
        IndiceDePuntosCompartidos.de(p.map { (cl, cob, pt) -> PuntoDeLaRuta(cl, cob, pt) })

    @Test
    fun `un punto de un solo cliente no esta compartido`() {
        val indice = puntos(
            Triple(1, "Rocío Manzano", PUERTA),
            Triple(1, "Rocío Manzano", desplazado(PUERTA, 3.0))
        )
        assertFalse(indice.esCompartido(PUERTA))
        assertEquals(1, indice.clientesQueComparten(PUERTA))
    }

    @Test
    fun `cuatro clientes todavia no alcanzan el umbral`() {
        val indice = puntos(*(1..4).map { Triple(it, "c", PUERTA) }.toTypedArray())
        assertEquals(4, indice.clientesQueComparten(PUERTA))
        assertFalse(indice.esCompartido(PUERTA))
    }

    @Test
    fun `cinco clientes distintos marcan el punto`() {
        val indice = puntos(*(1..5).map { Triple(it, "c", PUERTA) }.toTypedArray())
        assertEquals(5, indice.clientesQueComparten(PUERTA))
        assertTrue(indice.esCompartido(PUERTA))
    }

    @Test
    fun `el mismo cliente cinco veces NO marca el punto`() {
        // Lo que marca es la variedad de clientes, no el volumen de pagos. Sin
        // esta distinción, cualquier cliente asiduo se auto-descalificaría de
        // tener puerta.
        val indice =
            puntos(*(1..5).map { Triple(7, "c", desplazado(PUERTA, it * 1.0)) }.toTypedArray())
        assertEquals(1, indice.clientesQueComparten(PUERTA))
        assertFalse(indice.esCompartido(PUERTA))
    }

    @Test
    fun `cuenta cobradores distintos, que es lo que separa la tienda del punto de uno`() {
        val tienda =
            puntos(*(1..30).map { Triple(it, "cobrador-$it", PUNTO_MUY_COMPARTIDO) }.toTypedArray())
        val deUnCobrador =
            puntos(
                *(1..30).map { Triple(it, "Rocío Manzano", PUNTO_MUY_COMPARTIDO) }.toTypedArray()
            )
        assertEquals(30, tienda.cobradoresQueComparten(PUNTO_MUY_COMPARTIDO))
        assertEquals(1, deUnCobrador.cobradoresQueComparten(PUNTO_MUY_COMPARTIDO))
        assertTrue(tienda.esCompartido(PUNTO_MUY_COMPARTIDO))
        assertTrue(deUnCobrador.esCompartido(PUNTO_MUY_COMPARTIDO))
    }

    @Test
    fun `el vecindario cubre la frontera de la celda`() {
        // El caso que motiva mirar las ocho vecinas: cinco clientes a ~6 m de
        // distancia pueden caer del otro lado de una frontera de rejilla. Sin
        // vecindario, el punto quedaría sin marcar por un accidente de dónde
        // cayó el borde.
        val indice = puntos(
            *(1..5).map { Triple(it, "c", desplazado(PUERTA, it * 1.5, it * 1.5)) }.toTypedArray()
        )
        assertTrue(indice.esCompartido(PUERTA))
    }

    @Test
    fun `un punto lejano no se contagia`() {
        val indice = puntos(*(1..20).map { Triple(it, "c", PUNTO_MUY_COMPARTIDO) }.toTypedArray())
        assertFalse(
            "la marca se corrió a un punto que no comparte nadie",
            indice.esCompartido(PUERTA)
        )
        assertEquals(0, indice.clientesQueComparten(PUERTA))
    }

    @Test
    fun `un indice vacio no marca nada`() {
        val indice = IndiceDePuntosCompartidos.de(emptyList())
        assertFalse(indice.esCompartido(PUERTA))
        assertEquals(0, indice.clientesQueComparten(PUERTA))
    }

    @Test
    fun `contar solo la zona nunca cuenta de mas`() {
        // **La propiedad que hace seguro el umbral local.** Los clientes que el
        // teléfono ve son un SUBCONJUNTO de los de la empresa, así que su cuenta
        // nunca puede exceder la del servidor: un >=5 local es un >=5 real. Lo
        // único que un umbral local puede perder es alcance, jamás certeza.
        val todaLaEmpresa = (1..40).map {
            PuntoDeLaRuta(
                it,
                "cobrador-${it % 6}",
                PUNTO_MUY_COMPARTIDO
            )
        }
        val soloUnaZona = todaLaEmpresa.filter { it.clienteId % 6 == 0 }
        val global = IndiceDePuntosCompartidos.de(todaLaEmpresa)
        val local = IndiceDePuntosCompartidos.de(soloUnaZona)
        assertTrue(
            "el conteo local excedió al global",
            local.clientesQueComparten(PUNTO_MUY_COMPARTIDO) <=
                global.clientesQueComparten(PUNTO_MUY_COMPARTIDO)
        )
        assertTrue(
            "el punto compartido se perdió con el umbral local",
            local.esCompartido(PUNTO_MUY_COMPARTIDO)
        )
    }

    @Test
    fun `la celda mide doce metros de lado en los dos ejes`() {
        // Si alguien quitara la corrección por coseno de la latitud, la celda
        // sería un rectángulo y esta prueba se caería en el eje este-oeste.
        val centro = IndiceDePuntosCompartidos.celdaDe(PUERTA)
        val aVeinteAlNorte = IndiceDePuntosCompartidos.celdaDe(desplazado(PUERTA, 20.0))
        val aVeinteAlEste = IndiceDePuntosCompartidos.celdaDe(desplazado(PUERTA, 0.0, 20.0))
        assertTrue("20 m al norte cayeron en la misma celda", centro != aVeinteAlNorte)
        assertTrue("20 m al este cayeron en la misma celda", centro != aVeinteAlEste)
    }
}
