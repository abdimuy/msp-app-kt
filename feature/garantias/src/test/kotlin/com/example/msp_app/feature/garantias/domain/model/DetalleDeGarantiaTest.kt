package com.example.msp_app.feature.garantias.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Resolver por clave, no por orden ni por fecha** (prueba 4 del brief,
 * mutante A6).
 *
 * El detalle falso tiene **otro** evento del mismo tipo antes del que se busca,
 * a propósito: si alguien tomara "el primero" o "el último" de la línea de
 * tiempo, esta prueba se pondría roja. El artículo sale del `articulo_ref` del
 * evento con la clave, que es lo único que el servidor garantiza.
 */
class DetalleDeGarantiaTest {

    private val ahora: Instant = Instant.parse("2026-11-10T18:00:00Z")

    private val detalle = DetalleDeGarantia(
        id = "folio-1",
        folio = "GA-000001",
        origen = OrigenDeFolio.CLIENTE,
        estado = "en_proceso",
        descripcion = "Silla rota",
        abiertoPor = "cobrador",
        creadoEn = ahora,
        actualizadoEn = ahora,
        articulos = listOf(
            articulo(id = "art-a", clave = "SILLA"),
            articulo(id = "art-b", clave = "MESA")
        ),
        eventos = listOf(
            evento(id = "ev-1", clave = "clave-de-otro", articuloRef = "art-a"),
            evento(id = "ev-2", clave = "clave-buscada", articuloRef = "art-b")
        )
    )

    @Test
    fun `el evento se toma por su clave y no por ser el primero`() {
        assertEquals("ev-2", detalle.eventoPorClave("clave-buscada")?.id)
    }

    @Test
    fun `el articulo sale del articulo ref del evento con la clave`() {
        assertEquals("art-b", detalle.articuloDeLaClave("clave-buscada")?.id)
    }

    @Test
    fun `no se confunde con otro evento del mismo tipo anterior`() {
        val encontrado = detalle.articuloDeLaClave("clave-buscada")
        assertTrue(encontrado?.id != "art-a")
        assertEquals("MESA", encontrado?.clave)
    }

    @Test
    fun `una clave que el servidor todavia no tiene no inventa un articulo`() {
        assertNull(detalle.eventoPorClave("clave-inexistente"))
        assertNull(detalle.articuloDeLaClave("clave-inexistente"))
    }

    @Test
    fun `un evento sin articulo ref no resuelve articulo`() {
        val sinArticulo = detalle.copy(
            eventos = listOf(evento(id = "ev-3", clave = "clave-suelta", articuloRef = null))
        )
        assertNull(sinArticulo.articuloDeLaClave("clave-suelta"))
    }

    @Test
    fun `un folio cerrado lo dice`() {
        assertFalse(detalle.estaCerrado)
        assertTrue(detalle.copy(cerradoEn = ahora).estaCerrado)
    }

    @Test
    fun `el articulo se busca por su id del servidor`() {
        assertEquals("SILLA", detalle.articulo("art-a")?.clave)
        assertNull(detalle.articulo("art-z"))
    }

    private fun articulo(id: String, clave: String): ArticuloDeGarantia = ArticuloDeGarantia(
        id = id,
        rol = "original",
        clave = clave,
        descripcion = clave,
        etapa = "recolectado",
        ubicacion = "en_transito",
        creadoEn = ahora,
        actualizadoEn = ahora
    )

    private fun evento(id: String, clave: String, articuloRef: String?): EventoDeGarantia =
        EventoDeGarantia(
            id = id,
            articuloRef = articuloRef,
            tipo = "recoleccion",
            descripcion = "Recolección",
            usuario = "cobrador",
            creadoEn = ahora,
            deviceCreatedAt = ahora,
            claveIdempotencia = clave
        )
}
