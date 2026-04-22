package com.example.msp_app.data.api.services.localSales

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica que LocalSaleResponse pueda deserializar correctamente los dos shapes
 * de respuesta 2xx que el backend produce hoy:
 *
 *  1. Creación nueva   -> body.message + body.localSaleId + body.imagenesSubidas + ...
 *  2. Reintento idempotente (yaExistia:true) -> campos distintos (mensaje, yaExistia, productosRegistrados),
 *     que NO están declarados en el modelo y deben ser ignorados silenciosamente por Gson.
 *
 * Cualquier excepción aquí significaría que PendingLocalSalesWorker estallaría al
 * leer `response.body?.localSaleId` y la venta NO se marcaría como ENVIADA.
 */
class LocalSaleResponseTest {

    private val gson = Gson()

    @Test
    fun `deserializa respuesta de creacion nueva 200`() {
        val json = """
            {
              "error": "",
              "body": {
                "localSaleId": "uuid-de-la-venta",
                "combosRegistrados": 2,
                "traspasoOmitido": false,
                "message": "Venta local creada exitosamente",
                "imagenesSubidas": 3
              }
            }
        """.trimIndent()

        val response = gson.fromJson(json, LocalSaleResponse::class.java)

        assertEquals("", response.error)
        assertNotNull(response.body)
        assertEquals("uuid-de-la-venta", response.body?.localSaleId)
        assertEquals(2, response.body?.combosRegistrados)
        assertEquals(false, response.body?.traspasoOmitido)
        assertEquals("Venta local creada exitosamente", response.body?.message)
        assertEquals(3, response.body?.imagenesSubidas)
    }

    @Test
    fun `deserializa respuesta de reintento idempotente yaExistia true 200`() {
        // Este es el payload que el backend devuelve cuando WorkManager reintenta
        // una venta que ya fue procesada. Trae campos (success, yaExistia, mensaje,
        // productosRegistrados) que NO están declarados en LocalSaleResponseBody —
        // Gson debe ignorarlos sin lanzar excepción.
        val json = """
            {
              "error": "",
              "body": {
                "success": true,
                "localSaleId": "uuid-ya-existia",
                "mensaje": "Venta local con ID uuid-ya-existia ya fue procesada anteriormente",
                "yaExistia": true,
                "productosRegistrados": 0
              }
            }
        """.trimIndent()

        val response = gson.fromJson(json, LocalSaleResponse::class.java)

        assertEquals("", response.error)
        assertNotNull(response.body)
        assertEquals("uuid-ya-existia", response.body?.localSaleId)
        // Campos desconocidos por el modelo: no deben romper la deserialización.
        // Los campos conocidos que no vienen en este shape quedan en null — el worker
        // los lee con `?: ""` / `?: 0` así que eso está cubierto.
        assertNull(response.body?.message)
        assertNull(response.body?.combosRegistrados)
        assertNull(response.body?.imagenesSubidas)
        assertNull(response.body?.traspasoOmitido)
    }

    @Test
    fun `deserializa respuesta 2xx con body vacio sin lanzar`() {
        // Caso defensivo: si el backend alguna vez responde 2xx con body:{} o sin body,
        // el worker debe seguir marcando la venta como enviada (confiamos en el 2xx).
        val json = """
            {
              "error": "",
              "body": {}
            }
        """.trimIndent()

        val response = gson.fromJson(json, LocalSaleResponse::class.java)

        assertEquals("", response.error)
        assertNotNull(response.body)
        assertNull(response.body?.localSaleId)
        assertNull(response.body?.combosRegistrados)
        assertNull(response.body?.imagenesSubidas)
    }

    @Test
    fun `el worker puede leer body localSaleId sin NPE cuando body es null`() {
        // Smoke test del operador safe-call que usa el worker al loguear:
        //   response.body?.localSaleId ?: ""
        //   response.body?.imagenesSubidas ?: 0
        //   response.body?.combosRegistrados ?: 0
        val json = """{"error":"","body":null}"""

        val response = gson.fromJson(json, LocalSaleResponse::class.java)

        assertNull(response.body)
        // Reproducimos literalmente las expresiones del worker:
        val serverSaleId: String = response.body?.localSaleId ?: ""
        val imagesUploaded: Int = response.body?.imagenesSubidas ?: 0
        val combosRegistrados: Int = response.body?.combosRegistrados ?: 0
        assertEquals("", serverSaleId)
        assertEquals(0, imagesUploaded)
        assertEquals(0, combosRegistrados)
    }

    @Test
    fun `campos extra en el root se ignoran`() {
        // Por si el backend alguna vez agrega un campo nuevo al root (por ejemplo,
        // "timestamp" o "requestId"), Gson debe ignorarlo sin romper el parseo.
        val json = """
            {
              "error": "",
              "timestamp": "2026-04-15T12:00:00Z",
              "requestId": "req-123",
              "body": {
                "localSaleId": "uuid-con-extras"
              }
            }
        """.trimIndent()

        val response = gson.fromJson(json, LocalSaleResponse::class.java)

        assertEquals("", response.error)
        assertEquals("uuid-con-extras", response.body?.localSaleId)
    }

    @Test
    fun `error vacio confirma que es un caso de exito para el worker`() {
        // El worker actual no lee response.error (confía en el status 2xx de Retrofit),
        // pero este test documenta el contrato acordado con el backend: en 2xx, error siempre = "".
        val json = """{"error":"","body":{"localSaleId":"x"}}"""

        val response = gson.fromJson(json, LocalSaleResponse::class.java)

        assertTrue(response.error.isEmpty())
    }
}
