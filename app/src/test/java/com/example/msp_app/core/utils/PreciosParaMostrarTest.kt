package com.example.msp_app.core.utils

import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.data.models.productInventory.toEntity
import com.example.msp_app.utils.PriceParser
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * E-APP-059 (orden de pantalla) y E-APP-060 (precio de la tarjeta del selector).
 * Se prueban con los dos formatos que existen: la cadena del servidor
 * (`Nombre:precio, ...`) y el JSON que guarda Room (`ProductInventory.toEntity`).
 */
class PreciosParaMostrarTest {

    private val lista = "Precio de lista" to 1600.0
    private val corto = "Precio 4 Meses" to 1200.0
    private val contado = "Precio 1 Meses" to 1000.0

    private fun cadena(vararg p: Pair<String, Double>) = p.joinToString(",") {
        "${it.first}:${it.second}"
    }

    private fun json(vararg p: Pair<String, Double>): String = ProductInventory(
        ARTICULO_ID = 1,
        ARTICULO = "X",
        EXISTENCIAS = 1,
        LINEA_ARTICULO_ID = 1,
        LINEA_ARTICULO = "L",
        PRECIOS = cadena(*p)
    ).toEntity().PRECIOS!!

    private val canonico = listOf(contado.first, corto.first, lista.first)

    @Test
    fun `E-APP-059 orden medido en dev (lista, 4, 1) se pinta contado, corto, lista`() {
        assertEquals(canonico, preciosParaMostrar(cadena(lista, corto, contado)).map { it.first })
        assertEquals(canonico, preciosParaMostrar(json(lista, corto, contado)).map { it.first })
    }

    @Test
    fun `E-APP-059 orden canonico nuevo del servidor sigue saliendo canonico`() {
        // Con `asReversed()` (el carrito de antes) esto salía lista primero.
        assertEquals(canonico, preciosParaMostrar(cadena(contado, corto, lista)).map { it.first })
        assertEquals(canonico, preciosParaMostrar(json(contado, corto, lista)).map { it.first })
    }

    @Test
    fun `E-APP-060 el defecto - values lastOrNull da el de lista con el orden canonico`() {
        // La expresión que tenían ModernProductSelector:282 y SimpleProductSelector:223.
        val viejo = parsePriceJsonToMap(cadena(contado, corto, lista)).values.lastOrNull()
        assertEquals(lista.second, viejo)
        assertNotEquals(contado.second, viejo)
    }

    @Test
    fun `E-APP-060 la tarjeta del selector muestra el contado con el orden canonico`() {
        assertEquals(contado.second, precioDeContadoDeTarjeta(cadena(contado, corto, lista)))
        assertEquals(contado.second, precioDeContadoDeTarjeta(json(contado, corto, lista)))
    }

    @Test
    fun `E-APP-059 E-APP-060 propiedad - toda permutacion da el mismo orden, el mismo contado y el mismo envio`() {
        val base = listOf(lista, corto, contado, "Mayoreo" to 800.0)
        repeat(SEMILLAS) { semilla ->
            val rnd = Random(semilla)
            val precios = base.filter { rnd.nextInt(5) != 0 }
                .map { it.first to rnd.nextInt(1, 90_000).toDouble() }
            val ref = cadena(*precios.toTypedArray())
            val ordenRef = preciosParaMostrar(ref)
            val contadoRef = precioDeContadoDeTarjeta(ref)
            val envioRef = PriceParser.parsePricesFromString(ref)
            repeat(PERMUTACIONES) {
                val perm = precios.shuffled(rnd).toTypedArray()
                for (formato in listOf(cadena(*perm), json(*perm))) {
                    assertEquals("semilla $semilla", ordenRef, preciosParaMostrar(formato))
                    assertEquals("semilla $semilla", contadoRef, precioDeContadoDeTarjeta(formato))
                }
                // El camino de dinero (PriceParser busca por nombre) tampoco depende del orden.
                assertEquals(
                    "semilla $semilla",
                    envioRef,
                    PriceParser.parsePricesFromString(cadena(*perm))
                )
            }
            assertEquals(
                "semilla $semilla",
                precios.firstOrNull { it.first == contado.first }?.second,
                contadoRef
            )
        }
    }

    private companion object {
        const val SEMILLAS = 300
        const val PERMUTACIONES = 5
    }
}
