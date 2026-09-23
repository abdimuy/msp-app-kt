package com.example.msp_app.feature.pagos.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La búsqueda mira los CINCO datos que concatenaba la pantalla vieja
 * (`SalesScreen.kt:68`, retirada por la Task 21) — nombre, folio, calle, ciudad
 * y teléfono — más los
 * folios de todas las ventas del cliente.
 *
 * Un caso por campo: si mañana alguien quita uno de la concatenación, hay un
 * test que lo dice en vez de un cobrador que no encuentra a nadie por su calle.
 */
class BusquedaDeClientesTest {

    private val texto = BusquedaDeClientes.textoBuscable(
        listOf(
            "Victoria Flores Olmedo",
            "C. Hidalgo 214, Tepeaca",
            "238 162 7597",
            "V-5021",
            "V-5188"
        )
    )

    @Test
    fun `encuentra por nombre`() {
        assertTrue(BusquedaDeClientes.coincide(texto, "Victoria"))
    }

    @Test
    fun `encuentra por apellido`() {
        assertTrue(BusquedaDeClientes.coincide(texto, "Olmedo"))
    }

    @Test
    fun `encuentra por calle`() {
        assertTrue(BusquedaDeClientes.coincide(texto, "Hidalgo"))
    }

    @Test
    fun `encuentra por ciudad`() {
        assertTrue(BusquedaDeClientes.coincide(texto, "Tepeaca"))
    }

    @Test
    fun `encuentra por telefono`() {
        assertTrue(BusquedaDeClientes.coincide(texto, "162 7597"))
    }

    @Test
    fun `encuentra por el folio de la primera venta`() {
        assertTrue(BusquedaDeClientes.coincide(texto, "V-5021"))
    }

    @Test
    fun `encuentra por el folio de la SEGUNDA venta`() {
        // El defecto que la lista por cliente cierra: teclear el folio de la
        // otra cuenta tiene que traer a la misma persona.
        assertTrue(BusquedaDeClientes.coincide(texto, "V-5188"))
    }

    @Test
    fun `ignora acentos`() {
        val conAcentos = BusquedaDeClientes.textoBuscable(listOf("José Ramírez Muñoz"))
        assertTrue(BusquedaDeClientes.coincide(conAcentos, "jose ramirez"))
    }

    @Test
    fun `ignora mayusculas`() {
        assertTrue(BusquedaDeClientes.coincide(texto, "VICTORIA"))
    }

    @Test
    fun `una consulta en blanco no filtra a nadie`() {
        assertTrue(BusquedaDeClientes.coincide(texto, "   "))
    }

    @Test
    fun `lo que no esta no coincide`() {
        assertFalse(BusquedaDeClientes.coincide(texto, "Zacatecas"))
    }

    @Test
    fun `los campos vacios no ensucian el texto`() {
        val conHuecos = BusquedaDeClientes.textoBuscable(listOf("Ana López", "", "  ", "V-1"))
        assertTrue(BusquedaDeClientes.coincide(conHuecos, "ana lopez v-1"))
    }
}
