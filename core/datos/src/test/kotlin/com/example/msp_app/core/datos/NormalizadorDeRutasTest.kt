package com.example.msp_app.core.datos

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/** E-APP-170: la clave de un endpoint nunca lleva datos personales ni identificadores. */
class NormalizadorDeRutasTest {

    private fun clave(metodo: String, url: String) = NormalizadorDeRutas.clave(
        metodo,
        url.toHttpUrl()
    )

    @Test fun `numeros se reemplazan por id`() {
        assertEquals(
            "GET /v2/cobranza/pagos/{id}",
            clave("GET", "https://x.test/v2/cobranza/pagos/12345")
        )
    }

    @Test fun `zona se reemplaza y la version v2 se conserva`() {
        assertEquals(
            "GET /v2/cobranza/sync/pagos/zona/{id}/digest",
            clave("GET", "https://x.test/v2/cobranza/sync/pagos/zona/7/digest")
        )
    }

    @Test fun `uuid se reemplaza`() {
        assertEquals(
            "PATCH /v2/visitas/{id}/ubicacion",
            clave(
                "PATCH",
                "https://x.test/v2/visitas/3f2504e0-4f89-41d3-9a0c-0305e82c3301/ubicacion"
            )
        )
    }

    @Test fun `query string queda fuera`() {
        assertEquals(
            "GET /clientes",
            clave("GET", "https://x.test/clientes/?nombre=JUAN+PEREZ&telefono=5512345678")
        )
    }

    @Test fun `un folio con letras y numeros es id`() {
        assertEquals("GET /v2/ventas/{id}", clave("GET", "https://x.test/v2/ventas/AB-12345"))
    }

    @Test fun `un correo o texto largo es id y no se filtra`() {
        val k = clave("GET", "https://x.test/v2/usuarios/juan.perez@correo.com/ping")
        assertEquals("GET /v2/usuarios/{id}/ping", k)
        val largo = clave("GET", "https://x.test/v2/" + "a".repeat(40))
        assertEquals("GET /v2/{id}", largo)
    }

    @Test fun `host no forma parte de la clave`() {
        assertEquals(
            clave("GET", "https://uno.test/ping"),
            clave("GET", "https://otro.example:8443/ping")
        )
    }

    @Test fun `la raiz es una sola barra`() {
        assertEquals("GET /", clave("GET", "https://x.test/"))
    }

    @Test fun `rutas muy profundas se acotan`() {
        val k = clave("GET", "https://x.test/v2/cobranza/sync/pagos/zona/7/digest/ids/stream/ping")
        assertEquals("GET /v2/cobranza/sync/pagos/zona/{id}/digest/ids", k)
    }

    @Test fun `el metodo se normaliza a mayusculas`() {
        assertEquals("POST /ping", clave("post", "https://x.test/ping"))
    }

    // H5: `pathSegments` ya viene decodificado, así que una regla sobre `%` no veía nada.
    @Test fun `nombres con espacios o acentos nunca pasan`() {
        assertEquals(
            "GET /clientes/{id}",
            clave("GET", "https://x.test/clientes/Jos%C3%A9%20P%C3%A9rez")
        )
        assertEquals("GET /clientes/{id}", clave("GET", "https://x.test/clientes/juan%20perez"))
        assertEquals("GET /clientes/{id}/{id}", clave("GET", "https://x.test/clientes/Maria/Lopez"))
        assertEquals(
            "GET /v2/usuarios/{id}",
            clave("GET", "https://x.test/v2/usuarios/juan@correo.com")
        )
    }

    @Test fun `un literal que no es de las rutas de la app es id aunque sea alfabetico`() {
        assertEquals("GET /clientes/{id}", clave("GET", "https://x.test/clientes/garcia"))
        assertEquals("GET /{id}", clave("GET", "https://x.test/secreto"))
    }

    @Test fun `los literales conocidos de las rutas de la app se conservan`() {
        mapOf(
            "/v2/cobranza/sync/saldos/zona/1/ids" to "/v2/cobranza/sync/saldos/zona/{id}/ids",
            "/v2/visitas/sync/tareas/zona/1" to "/v2/visitas/sync/tareas/zona/{id}",
            "/v2/agenda-compromisos/conciliacion" to "/v2/agenda-compromisos/conciliacion",
            "/ventas-locales" to "/ventas-locales",
            "/garantias/1/eventos" to "/garantias/{id}/eventos",
            "/zonas-cliente" to "/zonas-cliente",
            "/almacenes/1" to "/almacenes/{id}",
            "/traspasos/costos" to "/traspasos/costos"
        ).forEach { (entra, sale) ->
            assertEquals(
                "GET $sale",
                clave("GET", "https://x.test$entra")
            )
        }
    }
}
