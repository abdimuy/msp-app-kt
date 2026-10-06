package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.navegacion.MenuFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasDelMenuTest {

    @Test
    fun `el buscador aparece con diez opciones`() {
        assertEquals(10, ReglasDelMenu.totalDeOpciones(MenuFixtures.todas))
        assertTrue(ReglasDelMenu.muestraBuscador(MenuFixtures.todas))
    }

    @Test
    fun `con nueve opciones no hay buscador`() {
        assertEquals(9, ReglasDelMenu.totalDeOpciones(MenuFixtures.nueve))
        assertFalse(ReglasDelMenu.muestraBuscador(MenuFixtures.nueve))
    }

    @Test
    fun `una seccion no plegada esta abierta`() {
        assertTrue(ReglasDelMenu.estaAbierta("VENTAS", emptySet(), null, emptySet(), ""))
    }

    @Test
    fun `una seccion plegada que no es la activa esta cerrada`() {
        assertFalse(ReglasDelMenu.estaAbierta("VENTAS", setOf("VENTAS"), "COBRO", emptySet(), ""))
    }

    @Test
    fun `la seccion activa se abre sola aunque el usuario la haya plegado`() {
        assertTrue(ReglasDelMenu.estaAbierta("COBRO", setOf("COBRO"), "COBRO", emptySet(), ""))
    }

    @Test
    fun `si el usuario toco la seccion activa en esta apertura manda su preferencia`() {
        assertFalse(ReglasDelMenu.estaAbierta("COBRO", setOf("COBRO"), "COBRO", setOf("COBRO"), ""))
    }

    @Test
    fun `con una busqueda escrita toda seccion con resultados va abierta`() {
        assertTrue(
            ReglasDelMenu.estaAbierta("VENTAS", setOf("VENTAS"), null, setOf("VENTAS"), "cat")
        )
    }

    @Test
    fun `una ruta sin seccion visible no abre nada que no exista`() {
        // Usuario sin VENTAS parado en una ruta de VENTAS: la activa no está entre las suyas.
        val visibles = listOf(MenuFixtures.cobro)
        val abiertas = visibles.filter {
            ReglasDelMenu.estaAbierta(it.id, setOf("COBRO"), "VENTAS", emptySet(), "")
        }
        assertEquals(emptyList<String>(), abiertas.map { it.id })
    }

    @Test
    fun `buscar sin acentos ni mayusculas encuentra garantias`() {
        val resultado = ReglasDelMenu.filtrar(MenuFixtures.todas, "  GARANTIAS ")
        assertEquals(listOf("GARANTIAS"), resultado.map { it.id })
        assertEquals(listOf("garantias"), resultado.single().items.map { it.id })
    }

    @Test
    fun `buscar deja solo las secciones con coincidencias`() {
        val resultado = ReglasDelMenu.filtrar(MenuFixtures.todas, "camioneta")
        assertEquals(listOf("VENTAS", "ALMACEN"), resultado.map { it.id })
        assertEquals(listOf("mi_camioneta"), resultado[0].items.map { it.id })
        assertEquals(listOf("asignacion"), resultado[1].items.map { it.id })
    }

    @Test
    fun `una busqueda sin coincidencias devuelve vacio`() {
        assertEquals(emptyList<Any>(), ReglasDelMenu.filtrar(MenuFixtures.todas, "nómina"))
    }

    @Test
    fun `una busqueda en blanco no filtra`() {
        assertEquals(MenuFixtures.todas, ReglasDelMenu.filtrar(MenuFixtures.todas, "   "))
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
