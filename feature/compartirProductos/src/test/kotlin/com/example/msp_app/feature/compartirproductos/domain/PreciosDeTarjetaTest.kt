package com.example.msp_app.feature.compartirproductos.domain

import com.example.msp_app.core.common.ventas.precios.TipoDePrecio
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-180: la tarjeta solo elige y ordena lo que la app ya clasificó. */
class PreciosDeTarjetaTest {

    private fun p(tipo: TipoDePrecio, monto: String) = PrecioDeCatalogo(tipo, BigDecimal(monto))

    private val todos = listOf(
        p(TipoDePrecio.CONTADO, "8990"),
        p(TipoDePrecio.CORTO_PLAZO, "10450"),
        p(TipoDePrecio.LARGO_PLAZO, "13200")
    )

    private fun etiquetas(r: List<PrecioDeTarjeta>) = r.map { it.fila.etiqueta to it.texto }

    @Test fun `las tres filas en orden contado corto credito con pesos sin centavos`() {
        val r = PreciosDeTarjeta.armar(todos, SeleccionDePrecios())
        assertEquals(
            listOf("Contado" to "$8,990", "Corto plazo" to "$10,450", "Crédito" to "$13,200"),
            etiquetas(r)
        )
    }

    @Test fun `el orden de llegada no cambia el orden de la tarjeta`() {
        val revuelto = listOf(todos[2], todos[0], todos[1])
        assertEquals(
            etiquetas(PreciosDeTarjeta.armar(todos, SeleccionDePrecios())),
            etiquetas(PreciosDeTarjeta.armar(revuelto, SeleccionDePrecios()))
        )
    }

    @Test fun `solo salen las filas marcadas`() {
        assertEquals(
            listOf(
                "Contado"
            ),
            etiquetas(PreciosDeTarjeta.armar(todos, SeleccionDePrecios(true, false, false))).map {
                it.first
            }
        )
        assertEquals(
            listOf(
                "Corto plazo"
            ),
            etiquetas(PreciosDeTarjeta.armar(todos, SeleccionDePrecios(false, true, false))).map {
                it.first
            }
        )
        assertEquals(
            listOf(
                "Crédito"
            ),
            etiquetas(PreciosDeTarjeta.armar(todos, SeleccionDePrecios(false, false, true))).map {
                it.first
            }
        )
        assertEquals(
            listOf(
                "Contado",
                "Crédito"
            ),
            etiquetas(PreciosDeTarjeta.armar(todos, SeleccionDePrecios(true, false, true))).map {
                it.first
            }
        )
    }

    @Test fun `ninguna casilla marcada no deja precios`() {
        assertTrue(PreciosDeTarjeta.armar(todos, SeleccionDePrecios(false, false, false)).isEmpty())
    }

    @Test fun `un precio faltante no sale aunque este marcado`() {
        val sinCorto = listOf(todos[0], todos[2])
        assertEquals(
            listOf(
                "Contado",
                "Crédito"
            ),
            PreciosDeTarjeta.armar(sinCorto, SeleccionDePrecios()).map {
                it.fila.etiqueta
            }
        )
        assertTrue(PreciosDeTarjeta.armar(emptyList(), SeleccionDePrecios()).isEmpty())
    }

    @Test fun `un tipo desconocido nunca llega a la tarjeta`() {
        val r = PreciosDeTarjeta.armar(
            listOf(p(TipoDePrecio.DESCONOCIDO, "999")),
            SeleccionDePrecios()
        )
        assertTrue(r.isEmpty())
    }

    @Test fun `montos en cero o negativos no se muestran`() {
        val raros =
            listOf(
                p(TipoDePrecio.CONTADO, "0"),
                p(TipoDePrecio.CORTO_PLAZO, "-50"),
                p(TipoDePrecio.LARGO_PLAZO, "13200")
            )
        assertEquals(
            listOf("Crédito" to "$13,200"),
            etiquetas(PreciosDeTarjeta.armar(raros, SeleccionDePrecios()))
        )
        assertTrue(
            PreciosDeTarjeta.armar(
                listOf(p(TipoDePrecio.CONTADO, "0.00")),
                SeleccionDePrecios()
            ).isEmpty()
        )
    }

    @Test fun `si hay dos del mismo tipo se toma el primero como hace la app`() {
        val dos = listOf(p(TipoDePrecio.CONTADO, "100"), p(TipoDePrecio.CONTADO, "200"))
        assertEquals(
            listOf("$100"),
            PreciosDeTarjeta.armar(dos, SeleccionDePrecios()).map { it.texto }
        )
    }

    @Test fun `montos grandes con separador de miles`() {
        assertEquals("$1,250,000", FormatoDePesos.formatear(BigDecimal("1250000")))
        assertEquals("$124,900", FormatoDePesos.formatear(BigDecimal("124900")))
        assertEquals("$1", FormatoDePesos.formatear(BigDecimal("1")))
        assertEquals("$99,999,999", FormatoDePesos.formatear(BigDecimal("99999999")))
    }

    @Test fun `los centavos se redondean como la app y no se muestran`() {
        // Igual que Double.toCurrency(noDecimals = true): NumberFormat es-MX, 0 decimales (HALF_EVEN).
        assertEquals("$8,990", FormatoDePesos.formatear(BigDecimal("8990.49")))
        assertEquals("$8,991", FormatoDePesos.formatear(BigDecimal("8990.51")))
        assertEquals("$1", FormatoDePesos.formatear(BigDecimal("0.99")))
    }

    @Test fun `la seleccion marca y desmarca fila por fila`() {
        val s = SeleccionDePrecios().con(FilaDePrecio.CORTO_PLAZO, false)
        assertEquals(SeleccionDePrecios(contado = true, cortoPlazo = false, credito = true), s)
        assertEquals(SeleccionDePrecios(), s.con(FilaDePrecio.CORTO_PLAZO, true))
        FilaDePrecio.entries.forEach { fila ->
            assertTrue(SeleccionDePrecios().incluye(fila))
            assertTrue(!SeleccionDePrecios(false, false, false).incluye(fila))
        }
    }

    @Test fun `credito es el precio de lista (largo plazo)`() {
        assertEquals(TipoDePrecio.LARGO_PLAZO, FilaDePrecio.CREDITO.tipo)
        assertEquals(TipoDePrecio.CONTADO, FilaDePrecio.CONTADO.tipo)
        assertEquals(TipoDePrecio.CORTO_PLAZO, FilaDePrecio.CORTO_PLAZO.tipo)
    }
}
