package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Los bordes del texto libre: **vacío contra nulo**, el tope exacto, y qué pasa
 * con los saltos de línea.
 *
 * El borde del tope se prueba en los tres puntos que importan —`NOTA_MAX - 1`,
 * `NOTA_MAX`, `NOTA_MAX + 1`— y no solo "un texto largo": un `>` puesto donde
 * iba un `>=` sobrevive a un test que solo prueba 10 000 caracteres.
 */
class NotaDeLaFichaTest {

    private val tope = FichaDelCliente.NOTA_MAX

    @Test
    fun `vacio y nulo colapsan a nulo - no a cadena vacia`() {
        assertNull(FichaDelCliente.limpia(null))
        assertNull(FichaDelCliente.limpia(""))
        assertNull(FichaDelCliente.limpia("   "))
        assertNull("solo saltos de linea tampoco es nota", FichaDelCliente.limpia("\n\n\t "))
    }

    @Test
    fun `recorta los extremos y deja el texto de adentro intacto`() {
        assertEquals("hay perro", FichaDelCliente.limpia("  hay perro \n"))
    }

    @Test
    fun `los saltos de linea de adentro se conservan - el cobrador escribe listas`() {
        val lista = "trabaja de noche\natiende la suegra\ncasa azul"
        assertEquals(lista, FichaDelCliente.limpia(lista))
    }

    @Test
    fun `justo en el tope no se corta nada`() {
        val exacta = "a".repeat(tope)
        assertEquals(exacta, FichaDelCliente.limpia(exacta))
        assertEquals(tope, FichaDelCliente.limpia(exacta)?.length)
    }

    @Test
    fun `uno menos que el tope tampoco se corta`() {
        val casi = "a".repeat(tope - 1)
        assertEquals(casi, FichaDelCliente.limpia(casi))
    }

    @Test
    fun `uno mas que el tope se corta al tope`() {
        val pasada = "a".repeat(tope + 1)
        assertEquals(tope, FichaDelCliente.limpia(pasada)?.length)
        assertEquals("a".repeat(tope), FichaDelCliente.limpia(pasada))
    }

    @Test
    fun `el corte no parte un emoji a la mitad`() {
        // Un carácter que ocupa DOS `Char` (par suplente). Con el corte crudo,
        // el último quedaría partido y ninguna fuente lo puede pintar.
        val perro = "🐕"
        val texto = "a".repeat(tope - 1) + perro
        val limpia = requireNotNull(FichaDelCliente.limpia(texto))
        assertEquals(tope - 1, limpia.length)
        assertEquals("a".repeat(tope - 1), limpia)
    }

    @Test
    fun `un emoji que si cabe entero se conserva`() {
        val perro = "🐕"
        val texto = "a".repeat(tope - 2) + perro
        assertEquals(texto, FichaDelCliente.limpia(texto))
    }

    @Test
    fun `si el corte deja solo espacios al final se recortan tambien`() {
        val texto = "hay perro" + " ".repeat(tope)
        assertEquals("hay perro", FichaDelCliente.limpia(texto))
    }
}
