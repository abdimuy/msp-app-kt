package com.example.msp_app.core.datos

import org.junit.Assert.assertEquals
import org.junit.Test

class ClasificadorDeServicioTest {

    private fun s(k: String) = ClasificadorDeServicio.servicioDe(k)

    @Test fun `lista de clientes`() = assertEquals("Lista de clientes", s("GET /clientes"))

    @Test fun `sincronizacion de pagos y ventas`() {
        assertEquals("Pagos y ventas (sincronización)", s("GET /v2/cobranza/sync/pagos/zona/{id}"))
        assertEquals("Pagos y ventas (sincronización)", s("GET /v2/cobranza/sync/ventas/zona/{id}"))
        assertEquals("Pagos y ventas (sincronización)", s("POST /v2/cobranza/pagos"))
        assertEquals("Pagos y ventas (sincronización)", s("GET /v2/ventas/{id}"))
    }

    @Test fun `cuadre con la oficina`() {
        assertEquals("Cuadre con la oficina", s("GET /v2/cobranza/sync/pagos/zona/{id}/digest"))
        assertEquals("Cuadre con la oficina", s("GET /v2/cobranza/sync/pagos/zona/{id}/ids"))
        assertEquals("Cuadre con la oficina", s("GET /v2/cobranza/sync/saldos/by-ids"))
        assertEquals("Cuadre con la oficina", s("GET /v2/visitas/sync/zona/{id}/digest"))
        assertEquals("Cuadre con la oficina", s("POST /v2/agenda-compromisos/conciliacion"))
    }

    @Test fun `avisos en vivo`() {
        assertEquals("Avisos en vivo", s("GET /v2/cobranza/sync/pagos/zona/{id}/stream"))
        assertEquals("Avisos en vivo", s("GET /v2/visitas/sync/visitas/zona/{id}/stream"))
    }

    @Test fun `fotos subida`() {
        assertEquals("Fotos (subida)", s(ClasificadorDeServicio.CLAVE_FOTOS))
        // H4: un pago/visita/venta multipart SIN foto es de su servicio, no de fotos.
        assertEquals("Pagos y ventas (sincronización)", s("POST /v2/cobranza/pagos"))
        assertEquals("Visitas", s("POST /v2/visitas"))
    }

    @Test fun `visitas`() {
        assertEquals("Visitas", s("GET /v2/visitas/{id}"))
        assertEquals("Visitas", s("POST /v2/visitas"))
        assertEquals("Visitas", s("GET /v2/visitas/sync/zona/{id}"))
    }

    @Test fun `imagenes de productos`() {
        assertEquals("Imágenes de productos", s("GET /imagenes"))
        assertEquals("Imágenes de productos", s(ClasificadorDeServicio.CLAVE_IMAGENES))
    }

    @Test fun `lo demas es otros`() {
        assertEquals("Otros", s("GET /ping"))
        assertEquals("Otros", s("OTROS"))
        assertEquals("Otros", s(ClasificadorDeServicio.CLAVE_DESCARGAS))
    }

    @Test fun `todo nombre de servicio empieza en mayuscula y no tiene punto final`() {
        ClasificadorDeServicio.TODOS.forEach {
            assertEquals(it, it.replaceFirstChar { c -> c.uppercase() })
            assertEquals(false, it.endsWith("."))
        }
    }
}
