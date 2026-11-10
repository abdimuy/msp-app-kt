package com.example.msp_app.core.datos

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/** E-APP-170: la clave de un endpoint nunca lleva datos personales ni identificadores. */
class NormalizadorDeRutasTest {

    private fun clave(metodo: String, url: String, multipart: Boolean = false) =
        NormalizadorDeRutas.clave(metodo, url.toHttpUrl(), multipart)

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
        val k = clave("GET", "https://x.test/v2/usuarios/juan.perez@correo.com/x")
        assertEquals("GET /v2/usuarios/{id}/x", k)
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

    @Test fun `multipart marca la clave`() {
        assertEquals(
            "POST /v2/visitas#foto",
            clave("POST", "https://x.test/v2/visitas", multipart = true)
        )
    }

    @Test fun `rutas muy profundas se acotan`() {
        val k = clave("GET", "https://x.test/a/b/c/d/e/f/g/h/i/j/k")
        assertEquals("GET /a/b/c/d/e/f/g/h", k)
    }

    @Test fun `el metodo se normaliza a mayusculas`() {
        assertEquals("POST /ping", clave("post", "https://x.test/ping"))
    }
}
