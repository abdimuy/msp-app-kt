package com.example.msp_app.core.utils

import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.data.models.productInventory.toEntity
import com.example.msp_app.utils.PriceParser
import com.google.gson.Gson
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

    private val cadenaVieja = "Precio de lista:1600.0, Precio 4 Meses:1200.0, Precio 1 Meses:1000.0"

    private fun deServidor(ordenadosJson: String?): ProductInventory {
        val campo = ordenadosJson?.let { ""","precios_ordenados":$it""" } ?: ""
        val json = """{"ARTICULO_ID":1,"ARTICULO":"X","EXISTENCIAS":1,"LINEA_ARTICULO_ID":1,""" +
            """"LINEA_ARTICULO":"L","PRECIOS":"$cadenaVieja"$campo}"""
        return Gson().fromJson(json, ProductInventory::class.java)
    }

    private fun ordenado(tipo: String, id: Int, nombre: String, precio: Double) =
        """{"tipo":"$tipo","precio_empresa_id":$id,"nombre":"$nombre","precio":$precio}"""

    @Test
    fun `E-APP-059 sin precios_ordenados Gson deja null y se cae a la cadena por nombre`() {
        val p = deServidor(null)
        assertEquals(null, p.preciosOrdenados)
        assertEquals(canonico, preciosParaMostrar(p).map { it.first })
        assertEquals(1000.0, precioDeContadoDeTarjeta(p))
    }

    @Test
    fun `E-APP-059 E-APP-060 con precios_ordenados se pinta por tipo del servidor en cualquier orden`() {
        val entradas = listOf(
            ordenado("largo_plazo", 42, "Precio de lista", 1600.0),
            ordenado("corto_plazo", 6925, "Precio 4 Meses", 1200.0),
            ordenado("contado", 8437, "Precio 1 Meses", 990.0)
        )
        val rnd = Random(SEMILLAS)
        repeat(PERMUTACIONES * 4) {
            val p = deServidor(entradas.shuffled(rnd).joinToString(",", "[", "]"))
            assertEquals(
                listOf(
                    "Precio 1 Meses" to 990.0,
                    "Precio 4 Meses" to 1200.0,
                    "Precio de lista" to 1600.0
                ),
                preciosParaMostrar(p)
            )
            // El contado de la tarjeta sale de precios_ordenados (990), no de la cadena (1000).
            assertEquals(990.0, precioDeContadoDeTarjeta(p))
        }
    }

    @Test
    fun `E-APP-059 precios_ordenados vacio se cae a la cadena`() {
        assertEquals(canonico, preciosParaMostrar(deServidor("[]")).map { it.first })
    }

    private companion object {
        const val SEMILLAS = 300
        const val PERMUTACIONES = 5
    }
}
