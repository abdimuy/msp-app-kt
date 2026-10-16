package com.example.msp_app.features.sales.upload.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** E-APP-068: el 422 del servidor nombra la línea y el vendedor la ve. */
class ProblemaDelServidorTest {

    private val mensaje =
        "en una venta de contado cada producto y cada combo debe llevar precio de contado mayor a cero"

    @Test
    fun `E-APP-068 nombra el articulo que rechazo el servidor`() {
        // Forma de huma.NewError en msp-api venthttp/auth.go:mapAppError.
        val body = """{"title":"Unprocessable Entity","status":422,"detail":"$mensaje",""" +
            """"errors":[{"message":"code=precio_contado_requerido"},""" +
            """{"message":"articulo=Cómoda","location":"linea.articulo","value":"Cómoda"}]}"""
        assertEquals("$mensaje\nArtículo: Cómoda", ProblemaDelServidor.leer(body).detail)
    }

    @Test
    fun `E-APP-068 nombra el combo`() {
        val body = """{"detail":"$mensaje","errors":[{"message":"combo=Recámara","location":"linea.combo","value":"Recámara"}]}"""
        assertEquals("$mensaje\nCombo: Recámara", ProblemaDelServidor.leer(body).detail)
    }

    @Test
    fun `sin linea o con cuerpo roto se comporta como antes`() {
        assertEquals(
            ProblemaDelServidor("c", "d"),
            ProblemaDelServidor.leer("""{"code":"c","detail":"d"}""")
        )
        assertEquals(
            ProblemaDelServidor(null, "m"),
            ProblemaDelServidor.leer("""{"message":"m","errors":[{"message":"code=x"}]}""")
        )
        assertEquals(ProblemaDelServidor(null, null), ProblemaDelServidor.leer("no es json"))
        assertEquals(ProblemaDelServidor(null, null), ProblemaDelServidor.leer(null))
    }
}
