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

    /**
     * El cuerpo exacto que arma `mapAppError` (msp-api-precios venthttp/auth.go):
     * `huma.NewError(status, ae.Message, ErrorDetail{Message: "code=" + ae.Code},
     * ErrorDetail{Message: "articulo=" + v, Location: "linea.articulo", Value: v})`,
     * serializado como `huma.ErrorModel` (title, status, detail, errors[]).
     */
    private val cuerpoDeHuma = """{"title":"Unprocessable Entity","status":422,"detail":"$mensaje",""" +
        """"errors":[{"message":"code=precio_contado_requerido"},""" +
        """{"message":"articulo=Cómoda","location":"linea.articulo","value":"Cómoda"}]}"""

    @Test
    fun `E-APP-069 el codigo sale de errors code= del cuerpo de Huma`() {
        val p = ProblemaDelServidor.leer(cuerpoDeHuma)
        assertEquals("precio_contado_requerido", p.code)
        assertEquals("$mensaje\nArtículo: Cómoda", p.detail)
    }

    @Test
    fun `E-APP-069 sin code= en errors se usa obj code de las formas viejas`() {
        assertEquals(
            "plazo_invalido",
            ProblemaDelServidor.leer("""{"code":"plazo_invalido","detail":"x"}""").code
        )
        assertEquals(
            "plazo_invalido",
            ProblemaDelServidor.leer(
                """{"code":"plazo_invalido","errors":[{"message":"code="}]}"""
            ).code
        )
    }

    @Test
    fun `sin linea o con cuerpo roto se comporta como antes`() {
        assertEquals(
            ProblemaDelServidor("c", "d"),
            ProblemaDelServidor.leer("""{"code":"c","detail":"d"}""")
        )
        assertEquals(
            ProblemaDelServidor("x", "m"),
            ProblemaDelServidor.leer("""{"message":"m","errors":[{"message":"code=x"}]}""")
        )
        assertEquals(ProblemaDelServidor(null, null), ProblemaDelServidor.leer("no es json"))
        assertEquals(ProblemaDelServidor(null, null), ProblemaDelServidor.leer(null))
    }
}
