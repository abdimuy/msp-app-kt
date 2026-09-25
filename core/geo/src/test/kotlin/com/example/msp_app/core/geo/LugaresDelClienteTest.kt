package com.example.msp_app.core.geo

import com.example.msp_app.core.geo.LugaresFixtures.PUERTA
import com.example.msp_app.core.geo.LugaresFixtures.PUNTO_MUY_COMPARTIDO
import com.example.msp_app.core.geo.LugaresFixtures.conPuntoCompartido
import com.example.msp_app.core.geo.LugaresFixtures.desplazado
import com.example.msp_app.core.geo.LugaresFixtures.medicion
import com.example.msp_app.core.geo.LugaresFixtures.sinPuntosCompartidos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La regla del título.** Es la prueba que más importa de este módulo: lo que
 * cuida es que la app no le diga "la puerta" a un lugar donde el cliente no
 * vive.
 */
class LugaresDelClienteTest {

    @Test
    fun `el grupo mayor no compartido es la puerta`() {
        val lugares = LugaresDelCliente.de(
            (0 until 6).map { medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong()) } +
                medicion(desplazado(PUERTA, 800.0), diasAtras = 40, id = "suelto"),
            sinPuntosCompartidos()
        )
        assertEquals(6, lugares.laPuerta?.conteo)
        assertFalse("nada debía quedar marcado", lugares.lugares.any { it.esCompartido })
    }

    @Test
    fun `un lugar compartido NO puede ser la puerta aunque sea el mayor`() {
        // Veinte mediciones en la tienda y tres en el domicilio real. El grupo
        // mayor es la tienda por siete a uno, y aun así el título es del chico.
        val mediciones =
            (0 until 20).map {
                medicion(
                    desplazado(PUNTO_MUY_COMPARTIDO, it * 0.5),
                    diasAtras = it.toLong(),
                    id = "tienda-$it"
                )
            } +
                (0 until 3).map {
                    medicion(desplazado(PUERTA, it * 2.0), diasAtras = 200L + it, id = "casa-$it")
                }
        val lugares = LugaresDelCliente.de(
            mediciones,
            conPuntoCompartido(PUNTO_MUY_COMPARTIDO, clientes = 381, cobradores = 30)
        )
        assertEquals("el mayor sigue siendo la tienda", 20, lugares.lugares.first().conteo)
        assertTrue("la tienda quedó sin marcar", lugares.lugares.first().esCompartido)
        assertEquals("el título se lo llevó el domicilio", 3, lugares.laPuerta?.conteo)
        assertFalse(lugares.laPuerta!!.esCompartido)
    }

    @Test
    fun `nada se esconde aunque no pueda ser la puerta`() {
        val lugares = LugaresDelCliente.de(
            (0 until 10).map {
                medicion(desplazado(PUNTO_MUY_COMPARTIDO, it * 0.5), diasAtras = it.toLong())
            },
            conPuntoCompartido(PUNTO_MUY_COMPARTIDO, clientes = 381, cobradores = 30)
        )
        assertEquals("el lugar se sigue dibujando", 1, lugares.lugares.size)
        assertEquals(10, lugares.lugares.single().conteo)
        assertNull("pero nadie se ganó el título", lugares.laPuerta)
        assertTrue(lugares.sinPuertaMedida)
    }

    @Test
    fun `la hoja puede decir cuantos comparten y cuantos cobran`() {
        val lugares = LugaresDelCliente.de(
            (0 until 4).map {
                medicion(
                    desplazado(PUNTO_MUY_COMPARTIDO, it * 0.5),
                    diasAtras = it.toLong()
                )
            },
            conPuntoCompartido(PUNTO_MUY_COMPARTIDO, clientes = 381, cobradores = 30)
        )
        val compartido = lugares.lugares.single()
        assertEquals(381, compartido.clientesQueLoComparten)
        assertEquals(30, compartido.cobradoresQueCobranAqui)
    }

    @Test
    fun `un lugar de puras transferencias no es la puerta`() {
        val lugares = LugaresDelCliente.de(
            (0 until 8).map {
                medicion(
                    desplazado(PUERTA, it * 1.0),
                    diasAtras = it.toLong(),
                    esTransferencia = true
                )
            },
            sinPuntosCompartidos()
        )
        assertEquals("se dibuja", 1, lugares.lugares.size)
        assertTrue(lugares.lugares.single().esSoloDeTransferencias)
        assertNull("no afirma un domicilio", lugares.laPuerta)
        assertTrue(lugares.sinPuertaMedida)
    }

    @Test
    fun `las transferencias se dibujan pero no votan`() {
        // Diez transferencias en un lado y tres abonos en efectivo en otro. Si
        // las transferencias votaran, el título se iría al grupo de diez.
        val lejos = desplazado(PUERTA, 400.0)
        val lugares = LugaresDelCliente.de(
            (0 until 10).map {
                medicion(desplazado(lejos, it * 0.5), diasAtras = it.toLong(), id = "tr-$it", esTransferencia = true)
            } +
                (0 until 3).map { medicion(desplazado(PUERTA, it * 2.0), diasAtras = 100L + it, id = "ef-$it") },
            sinPuntosCompartidos()
        )
        assertEquals("el grupo de transferencias es el mayor", 10, lugares.lugares.first().conteo)
        assertEquals("pero la puerta es la de los tres efectivos", 3, lugares.laPuerta?.conteo)
    }

    @Test
    fun `dos epocas sin traslape se leen como mudanza`() {
        val viejo = (0 until 5).map {
            medicion(desplazado(PUERTA, it * 2.0), diasAtras = 700L + it, id = "viejo-$it")
        }
        val nuevo = (0 until 5).map {
            medicion(
                desplazado(desplazado(PUERTA, 900.0), it * 2.0),
                diasAtras = 30L + it,
                id = "nuevo-$it"
            )
        }
        val lugares = LugaresDelCliente.de(viejo + nuevo, sinPuntosCompartidos())
        assertEquals(2, lugares.lugares.size)
        assertTrue("no se detectó la mudanza", lugares.pareceMudanza)
    }

    @Test
    fun `dos lugares que se traslapan en el tiempo NO son una mudanza`() {
        // El cliente cobra unas veces en un lado y otras en otro, en el mismo
        // periodo. Eso no es haberse mudado, y anunciarlo sería inventar.
        val unos = (0 until 5).map {
            medicion(desplazado(PUERTA, it * 2.0), diasAtras = (it * 20).toLong(), id = "a-$it")
        }
        val otros = (0 until 5).map {
            medicion(
                desplazado(desplazado(PUERTA, 900.0), it * 2.0),
                diasAtras = (it * 20 + 10).toLong(),
                id = "b-$it"
            )
        }
        assertFalse(LugaresDelCliente.de(unos + otros, sinPuntosCompartidos()).pareceMudanza)
    }

    @Test
    fun `dos lecturas sueltas no se leen como mudanza`() {
        // Un solo punto viejo lejos del resto es ruido de GPS, no una mudanza.
        val mediciones = (0 until 6).map {
            medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong(), id = "casa-$it")
        } + medicion(desplazado(PUERTA, 2000.0), diasAtras = 900, id = "ruido")
        assertFalse(LugaresDelCliente.de(mediciones, sinPuntosCompartidos()).pareceMudanza)
    }

    @Test
    fun `sin mediciones no hay puerta ni drama`() {
        val lugares = LugaresDelCliente.de(emptyList(), sinPuntosCompartidos())
        assertTrue(lugares.lugares.isEmpty())
        assertNull(lugares.laPuerta)
        assertFalse("sin datos no se anuncia que falte la puerta", lugares.sinPuertaMedida)
    }
}
