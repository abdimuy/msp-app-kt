package com.example.msp_app.feature.garantias.data.http

import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las plantillas de ruta de la captura y su resolución.
 *
 * ## Qué protege
 *
 * Un comando encolado sin señal guarda la plantilla con `{folio}` sin resolver.
 * Si un marcador quedara vivo, la petición pegaría contra un `{folio}` literal y
 * el servidor respondería 422 para siempre. La prueba fija que [RutasDeGarantia.resolver]
 * completa lo que tiene y **falla ruidosamente** cuando falta un id.
 */
class RutasDeGarantiaTest {

    @Test
    fun `el detalle resuelve el folio`() {
        assertEquals(
            "v2/garantias/folio-1",
            RutasDeGarantia.resolver(RutasDeGarantia.DETALLE, "folio-1")
        )
    }

    @Test
    fun `el avance resuelve folio y articulo`() {
        assertEquals(
            "v2/garantias/folio-1/articulos/articulo-9/avanzar",
            RutasDeGarantia.resolver(
                plantilla = RutasDeGarantia.AVANZAR_ARTICULO,
                folioId = "folio-1",
                articuloId = "articulo-9"
            )
        )
    }

    @Test
    fun `la subida de imagen resuelve folio y evento`() {
        assertEquals(
            "v2/garantias/folio-1/eventos/evento-7/imagenes",
            RutasDeGarantia.resolver(
                plantilla = RutasDeGarantia.IMAGENES,
                folioId = "folio-1",
                eventoId = "evento-7"
            )
        )
    }

    @Test
    fun `la apertura no tiene marcadores`() {
        assertEquals("v2/garantias", RutasDeGarantia.resolver(RutasDeGarantia.ABRIR, "x"))
    }

    @Test
    fun `un marcador sin resolver es un error`() {
        val excepcion = assertThrows(IllegalArgumentException::class.java) {
            RutasDeGarantia.resolver(RutasDeGarantia.AVANZAR_ARTICULO, "folio-1")
        }

        assertTrue(excepcion.message!!.contains("{articulo}"))
    }

    @Test
    fun `la ruta de imagenes sin evento es un error`() {
        assertThrows(IllegalArgumentException::class.java) {
            RutasDeGarantia.resolver(RutasDeGarantia.IMAGENES, "folio-1")
        }
    }
}
