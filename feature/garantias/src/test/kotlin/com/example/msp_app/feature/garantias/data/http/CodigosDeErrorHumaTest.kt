package com.example.msp_app.feature.garantias.data.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **El código de negocio de Huma vive en `errors[].message`, no en un campo
 * `code`.**
 *
 * Es la trampa que ya mordió en ventas: `obj["code"]` devuelve `null` contra
 * este API. La prueba fija las tres formas que el parser tiene que entender y
 * la que tiene que **ignorar**.
 */
class CodigosDeErrorHumaTest {

    @Test
    fun `el codigo sale de errors message`() {
        val cuerpo = """
            {
              "title": "Conflict",
              "status": 409,
              "detail": "la clave de idempotencia ya se usó con otro contenido",
              "errors": [{"message": "code=warranty_idempotency_key_mismatch"}]
            }
        """.trimIndent()

        assertEquals(
            "warranty_idempotency_key_mismatch",
            CodigosDeErrorHuma.codigo(cuerpo)
        )
    }

    @Test
    fun `el codigo tambien sale del detail si ahi aparece`() {
        val cuerpo = """
            {"title":"Unprocessable","status":422,"detail":"code=warranty_delivery_gps_required"}
        """.trimIndent()

        assertEquals("warranty_delivery_gps_required", CodigosDeErrorHuma.codigo(cuerpo))
    }

    @Test
    fun `un campo code en la raiz se ignora`() {
        val cuerpo = """
            {"title":"Conflict","status":409,"code":"warranty_idempotency_key_mismatch"}
        """.trimIndent()

        assertNull(CodigosDeErrorHuma.codigo(cuerpo))
    }

    @Test
    fun `el detalle es el texto en espanol del servidor`() {
        val cuerpo = """
            {
              "title": "Unprocessable Entity",
              "status": 422,
              "detail": "el gps del agente es obligatorio para entregar",
              "errors": [{"message": "code=warranty_delivery_gps_required"}]
            }
        """.trimIndent()

        assertEquals(
            "el gps del agente es obligatorio para entregar",
            CodigosDeErrorHuma.detalle(cuerpo)
        )
    }

    @Test
    fun `un cuerpo invalido no lanza y no trae codigo`() {
        assertNull(CodigosDeErrorHuma.codigo("{no es json"))
        assertNull(CodigosDeErrorHuma.detalle("{no es json"))
    }

    @Test
    fun `un cuerpo vacio no trae codigo ni detalle`() {
        assertNull(CodigosDeErrorHuma.codigo(null))
        assertNull(CodigosDeErrorHuma.codigo("   "))
        assertNull(CodigosDeErrorHuma.detalle(null))
    }

    @Test
    fun `un arreglo de errores sin codigo no inventa uno`() {
        val cuerpo = """
            {"title":"Bad Request","status":400,"errors":[{"message":"campo inválido"}]}
        """.trimIndent()

        assertNull(CodigosDeErrorHuma.codigo(cuerpo))
    }

    @Test
    fun `la respuesta junta status codigo y detalle`() {
        val cuerpo = """
            {"status":409,"detail":"la clave ya se usó con otro contenido",
             "errors":[{"message":"code=warranty_idempotency_key_mismatch"}]}
        """.trimIndent()

        val respuesta = CodigosDeErrorHuma.respuesta(status = 409, cuerpo = cuerpo)

        assertEquals(409, respuesta.status)
        assertEquals("warranty_idempotency_key_mismatch", respuesta.codigo)
        assertEquals("la clave ya se usó con otro contenido", respuesta.detalle)
    }
}
