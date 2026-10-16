package com.example.msp_app.core.common.ventas.precios

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** E-APP-059: orden y elección por NOMBRE, nunca por posición de llegada. */
class ClasificadorDePreciosTest {

    private val lista = "Precio de lista"
    private val corto = "Precio 4 Meses"
    private val contado = "Precio 1 Meses"

    @Test
    fun `E-APP-059 tipo por nombre segun las reglas de PriceParser`() {
        assertEquals(TipoDePrecio.CONTADO, ClasificadorDePrecios.tipoDe(contado))
        assertEquals(TipoDePrecio.CONTADO, ClasificadorDePrecios.tipoDe("Precio de Contado"))
        assertEquals(TipoDePrecio.CORTO_PLAZO, ClasificadorDePrecios.tipoDe(corto))
        assertEquals(TipoDePrecio.LARGO_PLAZO, ClasificadorDePrecios.tipoDe(lista))
        assertEquals(TipoDePrecio.LARGO_PLAZO, ClasificadorDePrecios.tipoDe("PRECIO DE LISTA"))
        assertEquals(TipoDePrecio.DESCONOCIDO, ClasificadorDePrecios.tipoDe("Precio 12 Meses"))
        assertEquals(TipoDePrecio.DESCONOCIDO, ClasificadorDePrecios.tipoDe("Mayoreo"))
    }

    @Test
    fun `E-APP-059 tipo del servidor en precios_ordenados`() {
        assertEquals(TipoDePrecio.CONTADO, ClasificadorDePrecios.tipoDeServidor("contado"))
        assertEquals(TipoDePrecio.CORTO_PLAZO, ClasificadorDePrecios.tipoDeServidor("corto_plazo"))
        assertEquals(TipoDePrecio.LARGO_PLAZO, ClasificadorDePrecios.tipoDeServidor("largo_plazo"))
        assertEquals(TipoDePrecio.DESCONOCIDO, ClasificadorDePrecios.tipoDeServidor(null))
        assertEquals(TipoDePrecio.DESCONOCIDO, ClasificadorDePrecios.tipoDeServidor("anual"))
    }

    @Test
    fun `E-APP-059 el orden medido en dev (lista, 4 meses, 1 mes) sale contado, corto, lista`() {
        // Así llegan 5,685 artículos en dev: al revés de lo pedido.
        val llegada = linkedMapOf(lista to 1600.0, corto to 1200.0, contado to 1000.0)
        assertEquals(
            listOf(contado, corto, lista),
            ClasificadorDePrecios.ordenar(llegada).map { it.first }
        )
    }

    @Test
    fun `E-APP-059 desconocidos al final y tipos ausentes no se inventan`() {
        val llegada = linkedMapOf("Mayoreo" to 900.0, lista to 1600.0, contado to 1000.0)
        assertEquals(
            listOf(contado, lista, "Mayoreo"),
            ClasificadorDePrecios.ordenar(llegada).map { it.first }
        )
        assertNull(ClasificadorDePrecios.precioDe(llegada, TipoDePrecio.CORTO_PLAZO))
    }

    @Test
    fun `E-APP-059 el nombre canonico de contado gana sobre el alias`() {
        val llegada = linkedMapOf("Precio de Contado" to 950.0, contado to 1000.0)
        assertEquals(1000.0, ClasificadorDePrecios.precioDe(llegada, TipoDePrecio.CONTADO))
    }

    @Test
    fun `E-APP-059 propiedad - cualquier permutacion da el mismo orden y el mismo contado`() {
        val nombres = listOf(lista, corto, contado, "Precio de Contado", "Mayoreo", "Promo")
        repeat(SEMILLAS) { semilla ->
            val rnd = Random(semilla)
            val elegidos = nombres.filter { rnd.nextBoolean() }
            val valores = elegidos.associateWith { rnd.nextInt(1, 100_000).toDouble() }
            val referencia = ClasificadorDePrecios.ordenar(valores)
            val contadoRef = ClasificadorDePrecios.precioDe(valores, TipoDePrecio.CONTADO)
            repeat(PERMUTACIONES) {
                val permutado = LinkedHashMap<String, Double>()
                elegidos.shuffled(rnd).forEach { permutado[it] = valores.getValue(it) }
                assertEquals(
                    "semilla $semilla",
                    referencia,
                    ClasificadorDePrecios.ordenar(permutado)
                )
                assertEquals(
                    "semilla $semilla",
                    contadoRef,
                    ClasificadorDePrecios.precioDe(permutado, TipoDePrecio.CONTADO)
                )
            }
            // Y el orden respeta los tipos: nunca un tipo "mayor" antes que uno "menor".
            val tipos = referencia.map { ClasificadorDePrecios.tipoDe(it.first).ordinal }
            assertEquals("semilla $semilla", tipos.sorted(), tipos)
        }
    }

    private companion object {
        const val SEMILLAS = 500
        const val PERMUTACIONES = 6
    }
}
