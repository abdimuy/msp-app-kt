package com.example.msp_app.feature.garantias.domain

import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada.FalloDeRed
import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La tabla completa de D6, una fila por prueba.**
 *
 * Lo que esta prueba protege no es "clasificar bien" en abstracto: es que un
 * 409 de huella no se reintente para siempre y que un `IOException` sí se
 * reintente. Los dos errores son los mutantes A3 y A4 del brief.
 */
class ClasificadorDeEnvioTest {

    @Test
    fun `un 200 se envia`() {
        assertEquals(DecisionDeEnvio.Enviado, clasificar(Respuesta(status = 200)))
    }

    @Test
    fun `un 201 se envia`() {
        assertEquals(DecisionDeEnvio.Enviado, clasificar(Respuesta(status = 201)))
    }

    @Test
    fun `un 204 se envia`() {
        assertEquals(DecisionDeEnvio.Enviado, clasificar(Respuesta(status = 204)))
    }

    @Test
    fun `un fallo de red se reintenta`() {
        assertEquals(
            DecisionDeEnvio.Reintentar,
            clasificar(FalloDeRed("sin conexión"))
        )
    }

    @Test
    fun `un 408 se reintenta`() {
        assertEquals(DecisionDeEnvio.Reintentar, clasificar(Respuesta(status = 408)))
    }

    @Test
    fun `un 425 se reintenta`() {
        assertEquals(DecisionDeEnvio.Reintentar, clasificar(Respuesta(status = 425)))
    }

    @Test
    fun `un 429 se reintenta`() {
        assertEquals(DecisionDeEnvio.Reintentar, clasificar(Respuesta(status = 429)))
    }

    @Test
    fun `un 500 se reintenta`() {
        assertEquals(DecisionDeEnvio.Reintentar, clasificar(Respuesta(status = 500)))
    }

    @Test
    fun `un 503 se reintenta`() {
        assertEquals(DecisionDeEnvio.Reintentar, clasificar(Respuesta(status = 503)))
    }

    @Test
    fun `un 401 pide renovar el token`() {
        assertEquals(DecisionDeEnvio.RenovarToken, clasificar(Respuesta(status = 401)))
    }

    @Test
    fun `el 409 de clave con otro contenido se rechaza`() {
        val decision = clasificar(
            Respuesta(
                status = 409,
                codigo = ClasificadorDeEnvio.CLAVE_CON_OTRO_CONTENIDO,
                detalle = "la clave ya se usó con otro contenido"
            )
        )
        assertTrue(decision is DecisionDeEnvio.Rechazado)
        assertFalse((decision as DecisionDeEnvio.Rechazado).sinPermiso)
    }

    @Test
    fun `el 409 de clave de otro folio se rechaza`() {
        val decision = clasificar(
            Respuesta(
                status = 409,
                codigo = ClasificadorDeEnvio.CLAVE_DE_OTRO_FOLIO,
                detalle = "la clave pertenece a otro folio"
            )
        )
        assertTrue(decision is DecisionDeEnvio.Rechazado)
    }

    @Test
    fun `cualquier otro 409 se reintenta`() {
        val decision = clasificar(
            Respuesta(
                status = 409,
                codigo = "warranty_idempotency_key_duplicate",
                detalle = "clave de idempotencia duplicada"
            )
        )
        assertEquals(DecisionDeEnvio.Reintentar, decision)
    }

    @Test
    fun `un 409 sin codigo se reintenta`() {
        assertEquals(DecisionDeEnvio.Reintentar, clasificar(Respuesta(status = 409)))
    }

    @Test
    fun `un 403 se rechaza como sin permiso`() {
        val decision = clasificar(Respuesta(status = 403))
        assertEquals(DecisionDeEnvio.Rechazado("Sin permiso", sinPermiso = true), decision)
    }

    @Test
    fun `un 422 se rechaza con el detalle del servidor`() {
        val decision = clasificar(
            Respuesta(
                status = 422,
                codigo = "warranty_delivery_gps_required",
                detalle = "el gps del agente es obligatorio para entregar"
            )
        )
        assertEquals(
            DecisionDeEnvio.Rechazado("el gps del agente es obligatorio para entregar"),
            decision
        )
    }

    @Test
    fun `un 4xx sin detalle se rechaza con un mensaje generico`() {
        assertEquals(
            DecisionDeEnvio.Rechazado("El servidor rechazó el envío"),
            clasificar(Respuesta(status = 400))
        )
    }

    @Test
    fun `un 404 se rechaza`() {
        assertTrue(clasificar(Respuesta(status = 404)) is DecisionDeEnvio.Rechazado)
    }

    private fun clasificar(resultado: ResultadoDeLaLlamada): DecisionDeEnvio =
        ClasificadorDeEnvio.clasificar(resultado)
}
