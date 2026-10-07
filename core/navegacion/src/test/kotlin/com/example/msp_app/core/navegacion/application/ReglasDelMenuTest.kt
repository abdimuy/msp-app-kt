package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.navegacion.MenuFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasDelMenuTest {

    @Test
    fun `una seccion no plegada esta abierta`() {
        assertTrue(ReglasDelMenu.estaAbierta("VENTAS", emptySet(), null, emptySet()))
    }

    @Test
    fun `una seccion plegada que no es la activa esta cerrada`() {
        assertFalse(ReglasDelMenu.estaAbierta("VENTAS", setOf("VENTAS"), "COBRO", emptySet()))
    }

    @Test
    fun `la seccion activa se abre sola aunque el usuario la haya plegado`() {
        assertTrue(ReglasDelMenu.estaAbierta("COBRO", setOf("COBRO"), "COBRO", emptySet()))
    }

    @Test
    fun `si el usuario toco la seccion activa en esta apertura manda su preferencia`() {
        assertFalse(ReglasDelMenu.estaAbierta("COBRO", setOf("COBRO"), "COBRO", setOf("COBRO")))
    }

    @Test
    fun `una ruta sin seccion visible no abre nada que no exista`() {
        // Usuario sin VENTAS parado en una ruta de VENTAS: la activa no está entre las suyas.
        val visibles = listOf(MenuFixtures.cobro)
        val abiertas = visibles.filter {
            ReglasDelMenu.estaAbierta(it.id, setOf("COBRO"), "VENTAS", emptySet())
        }
        assertEquals(emptyList<String>(), abiertas.map { it.id })
    }

    @Test
    fun `iniciales de las dos primeras palabras`() {
        assertEquals("ED", ReglasDelMenu.iniciales("efraín  Domínguez Reyes"))
    }

    @Test
    fun `iniciales de un nombre vacio es signo de interrogacion`() {
        assertEquals("?", ReglasDelMenu.iniciales("   "))
    }
}
