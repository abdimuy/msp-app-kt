package com.example.msp_app.feature.compartirproductos.domain

import com.example.msp_app.core.common.ventas.precios.TipoDePrecio
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmadorDeTarjetaTest {

    private val ahora = Instant.parse("2026-11-10T18:00:00Z")

    private fun articulo(
        nombre: String = "RECAMARA TOSCANA CON CABECERA TAPIZADA, BURO DOBLE",
        categoria: String? = "RECAMARAS"
    ) = ArticuloParaCompartir(
        id = 1,
        nombre = nombre,
        categoria = categoria,
        precios = listOf(
            PrecioDeCatalogo(TipoDePrecio.CONTADO, BigDecimal("8990")),
            PrecioDeCatalogo(TipoDePrecio.CORTO_PLAZO, BigDecimal("10450")),
            PrecioDeCatalogo(TipoDePrecio.LARGO_PLAZO, BigDecimal("13200"))
        ),
        fotoPath = null
    )

    @Test fun `arma titulo detalle categoria precios y leyenda`() {
        val t = ArmadorDeTarjeta.armar(articulo(), SeleccionDePrecios(), ahora)
        assertEquals("Recámara Toscana", t.titulo)
        assertEquals("Con cabecera tapizada, buró doble", t.detalle)
        assertEquals("Recámaras", t.categoria)
        assertEquals(3, t.precios.size)
        assertEquals("Precios vigentes al 10 nov 2026", t.vigencia)
    }

    @Test fun `el titulo sale siempre del corte automatico del nombre (E-APP-180)`() {
        val a = articulo()
        assertEquals(
            ArmadorDeTarjeta.tituloAutomatico(a),
            ArmadorDeTarjeta.armar(a, SeleccionDePrecios(), ahora).titulo
        )
        assertEquals(
            "Con cabecera tapizada, buró doble",
            ArmadorDeTarjeta.armar(a, SeleccionDePrecios(), ahora).detalle
        )
    }

    @Test fun `sin categoria o en blanco se omite y no se inventa`() {
        assertNull(
            ArmadorDeTarjeta.armar(
                articulo(categoria = null),
                SeleccionDePrecios(),
                ahora
            ).categoria
        )
        assertNull(
            ArmadorDeTarjeta.armar(
                articulo(categoria = "  "),
                SeleccionDePrecios(),
                ahora
            ).categoria
        )
    }

    @Test fun `nombre vacio cae a Producto`() {
        assertEquals(
            "Producto",
            ArmadorDeTarjeta.armar(articulo(nombre = ""), SeleccionDePrecios(), ahora).titulo
        )
        assertEquals("Producto", ArmadorDeTarjeta.tituloAutomatico(articulo(nombre = "  ")))
    }

    @Test fun `sin casillas la tarjeta sale sin precios pero con titulo y detalle`() {
        val t = ArmadorDeTarjeta.armar(
            articulo(),
            SeleccionDePrecios(false, false, false),
            ahora
        )
        assertTrue(t.precios.isEmpty())
        assertEquals("Recámara Toscana", t.titulo)
    }

    @Test fun `el texto del mensaje lleva el nombre completo y los precios elegidos`() {
        val texto = TextoDelMensaje.armar(
            listOf(articulo("SALA VALENCIA 3 PZAS C/CAJ. MAT.")),
            SeleccionDePrecios(true, false, true),
            ahora
        )
        assertEquals(
            "SALA VALENCIA 3 PZAS C/CAJ. MAT.\nContado: $8,990 · Crédito: $13,200\n\nPrecios vigentes al 10 nov 2026",
            texto
        )
    }

    @Test fun `el texto del mensaje con varios productos y sin precios`() {
        val texto = TextoDelMensaje.armar(
            listOf(articulo("SALA A"), articulo("SALA  B")),
            SeleccionDePrecios(false, false, false),
            ahora
        )
        assertEquals("SALA A\n\nSALA B\n\nPrecios vigentes al 10 nov 2026", texto)
    }

    @Test fun `el nombre completo va aunque el titulo se corte`() {
        val nombre = "COMEDOR ROBLE 6 SILLAS C/CRISTAL TEMPLADO"
        val texto = TextoDelMensaje.armar(listOf(articulo(nombre)), SeleccionDePrecios(), ahora)
        assertTrue(texto.startsWith(nombre))
    }
}
