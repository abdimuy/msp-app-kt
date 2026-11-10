package com.example.msp_app.feature.compartirproductos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeleccionDeProductosTest {

    private fun cambio(r: ResultadoDeSeleccion) = (r as ResultadoDeSeleccion.Cambio).seleccion

    @Test fun `empieza vacia e inactiva`() {
        val s = SeleccionDeProductos()
        assertFalse(s.activa)
        assertEquals(0, s.cantidad)
    }

    @Test fun `marcar y desmarcar`() {
        val uno = cambio(SeleccionDeProductos().alternar(7))
        assertTrue(uno.activa)
        assertTrue(uno.contiene(7))
        val ninguno = cambio(uno.alternar(7))
        assertFalse(ninguno.activa)
        assertFalse(ninguno.contiene(7))
    }

    @Test fun `conserva el orden en que se marcaron`() {
        var s = SeleccionDeProductos()
        listOf(5, 3, 9).forEach { s = cambio(s.alternar(it)) }
        assertEquals(listOf(5, 3, 9), s.ids)
    }

    @Test fun `treinta caben`() {
        var s = SeleccionDeProductos()
        repeat(30) { s = cambio(s.alternar(it)) }
        assertEquals(30, s.cantidad)
    }

    @Test fun `el treinta y uno se bloquea con su aviso y no cambia nada`() {
        var s = SeleccionDeProductos()
        repeat(30) { s = cambio(s.alternar(it)) }
        val r = s.alternar(100)
        assertEquals(ResultadoDeSeleccion.Excedido, r)
        assertEquals("Máximo 30 productos por envío", ResultadoDeSeleccion.Excedido.MENSAJE)
        assertEquals(30, s.cantidad)
        assertFalse(s.contiene(100))
    }

    @Test fun `con treinta se puede desmarcar y volver a marcar otro`() {
        var s = SeleccionDeProductos()
        repeat(30) { s = cambio(s.alternar(it)) }
        s = cambio(s.alternar(0))
        assertEquals(29, s.cantidad)
        s = cambio(s.alternar(100))
        assertEquals(30, s.cantidad)
    }

    @Test fun `el maximo es el que WhatsApp acepta`() {
        assertEquals(30, ResultadoDeSeleccion.MAXIMO)
    }
}
