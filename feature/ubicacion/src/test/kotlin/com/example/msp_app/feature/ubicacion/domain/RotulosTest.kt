package com.example.msp_app.feature.ubicacion.domain

import com.example.msp_app.core.geo.IndiceDePuntosCompartidos
import com.example.msp_app.core.geo.LugaresDelCliente
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.PUERTA
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.desplazado
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.indiceVacio
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.medicion
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.rutaCompartida
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Los rótulos aprobados, palabra por palabra.**
 *
 * Lo que estas pruebas cuidan no es la ortografía: es que la pantalla **no
 * nombre el lugar**. "La tienda" y "la casa del cobrador" son conclusiones que
 * la app no puede sostener —hay 50 celdas medidas que no se distinguen de una
 * vecindad de verdad— así que lo único que se dice son las dos cuentas.
 */
class RotulosTest {

    private fun lugaresDe(
        mediciones: List<com.example.msp_app.core.geo.MedicionDelCobro>,
        indice: IndiceDePuntosCompartidos = indiceVacio(),
        resaltado: String? = null
    ) = LugaresDelCliente.de(mediciones, indice).paraElMapa(resaltado)

    @Test
    fun `la puerta se rotula La puerta`() {
        val lugares = lugaresDe(
            (0 until 6).map { medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong()) }
        )
        assertEquals("La puerta", lugares.single().rotulo)
        assertEquals(TipoDeLugar.LA_PUERTA, lugares.single().tipo)
    }

    @Test
    fun `un punto compartido NO se llama tienda ni casa de nadie`() {
        val compartido = desplazado(PUERTA, 400.0)
        val lugares = lugaresDe(
            (0 until 6).map {
                medicion(desplazado(compartido, it * 0.5), diasAtras = it.toLong())
            },
            IndiceDePuntosCompartidos.de(
                rutaCompartida(compartido, clientes = 381, cobradores = 30)
            )
        )
        val enElMapa = lugares.single()
        assertEquals("Punto de muchos clientes", enElMapa.rotulo)
        assertEquals("Aquí cobran 30 cobradores a 381 clientes", enElMapa.detalle)
        // La prueba que de verdad importa: ninguna palabra prohibida.
        val prohibidas = listOf("tienda", "sucursal", "casa", "oficina", "cobrador ")
        prohibidas.forEach { palabra ->
            assertFalse(
                "el rótulo nombró el lugar: '${enElMapa.rotulo}'",
                enElMapa.rotulo.lowercase().contains(palabra)
            )
        }
    }

    @Test
    fun `el verbo concuerda con el numero de cobradores`() {
        // Lo cazó un golden, no un aserto: el aserto repetía el mismo error
        // que el código (nota hermana de E-INF-011). La primera versión decía "Aquí cobra 30
        // cobradores". En la única frase que sustituye al nombre del lugar, el
        // descuido le resta a lo que dice.
        fun detalleCon(cobradores: Int): String {
            val punto = desplazado(PUERTA, 400.0)
            return lugaresDe(
                (0 until 6).map {
                    medicion(desplazado(punto, it * 0.5), diasAtras = it.toLong())
                },
                IndiceDePuntosCompartidos.de(
                    rutaCompartida(punto, clientes = 40, cobradores = cobradores)
                )
            ).single().detalle
        }
        assertTrue("singular mal", detalleCon(1).startsWith("Aquí cobra un cobrador"))
        assertTrue("plural mal", detalleCon(7).startsWith("Aquí cobran 7 cobradores"))
        assertFalse(
            "se coló el singular con varios cobradores",
            detalleCon(7).startsWith("Aquí cobra ")
        )
    }

    @Test
    fun `con un solo cobrador la frase se ajusta y sigue sin nombrar`() {
        val compartido = desplazado(PUERTA, 400.0)
        val lugares = lugaresDe(
            (0 until 6).map {
                medicion(desplazado(compartido, it * 0.5), diasAtras = it.toLong())
            },
            IndiceDePuntosCompartidos.de(rutaCompartida(compartido, clientes = 48, cobradores = 1))
        )
        assertEquals("Aquí cobra un cobrador a 48 clientes", lugares.single().detalle)
    }

    @Test
    fun `un lugar de puras transferencias lo dice y no afirma domicilio`() {
        val lugares = lugaresDe(
            (0 until 6).map {
                medicion(
                    desplazado(PUERTA, it * 1.0),
                    diasAtras = it.toLong(),
                    esTransferencia = true
                )
            }
        )
        val enElMapa = lugares.single()
        assertEquals("Transferencias", enElMapa.rotulo)
        assertEquals("Se registró donde estaba el cobrador", enElMapa.detalle)
        assertEquals(TipoDeLugar.SOLO_TRANSFERENCIAS, enElMapa.tipo)
    }

    @Test
    fun `los rotulos caben en dos a cuatro palabras`() {
        // El registro de UI del repo: 2-4 palabras en el rótulo, el detalle en
        // la hoja. Se mide sobre los cuatro rótulos posibles.
        TipoDeLugar.entries.forEach { tipo ->
            val rotulo = when (tipo) {
                TipoDeLugar.LA_PUERTA -> "La puerta"
                TipoDeLugar.COMPARTIDO -> "Punto de muchos clientes"
                TipoDeLugar.SOLO_TRANSFERENCIAS -> "Transferencias"
                TipoDeLugar.OTRO -> "Otro punto"
            }
            val palabras = rotulo.split(" ").size
            assertTrue("'$rotulo' tiene $palabras palabras", palabras in 1..4)
            assertTrue("'$rotulo' no empieza con mayúscula", rotulo.first().isUpperCase())
            assertFalse("'$rotulo' termina en punto", rotulo.endsWith("."))
        }
    }

    @Test
    fun `el resaltado es el lugar que contiene el pago que se toco`() {
        val lejos = desplazado(PUERTA, 600.0)
        val mediciones =
            (0 until 4).map {
                medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong(), id = "casa-$it")
            } +
                medicion(lejos, diasAtras = 50, id = "el-que-toque")
        val lugares = lugaresDe(mediciones, resaltado = "el-que-toque")
        assertEquals(1, lugares.count { it.resaltado })
        assertTrue(
            lugares.single { it.resaltado }.lugar.mediciones.any { it.pagoId == "el-que-toque" }
        )
    }

    @Test
    fun `sin pago resaltado no se resalta nada`() {
        val lugares = lugaresDe(
            (0 until 4).map { medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong()) }
        )
        assertTrue(lugares.none { it.resaltado })
    }

    @Test
    fun `la frescura va de cero a uno dentro del rango del cliente`() {
        val viejo = medicion(desplazado(PUERTA, 900.0), diasAtras = 700, id = "v")
        val nuevos = (0 until 4).map {
            medicion(
                desplazado(PUERTA, it * 2.0),
                diasAtras = it.toLong()
            )
        }
        val lugares = lugaresDe(nuevos + viejo)
        val masViejo = (nuevos + viejo).minOf { it.fecha }
        val masNuevo = (nuevos + viejo).maxOf { it.fecha }
        val reciente = lugares.first { it.lugar.conteo > 1 }
        val antiguo = lugares.first { it.lugar.conteo == 1 }
        assertEquals(1f, reciente.frescura(masViejo, masNuevo), 0.01f)
        assertEquals(0f, antiguo.frescura(masViejo, masNuevo), 0.01f)
    }
}
