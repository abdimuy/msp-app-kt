package com.example.msp_app.data.api.services.visits

import java.net.HttpURLConnection
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Contrato de cable de `GET /v2/visitas/{id}` (plan visitas P1, Tarea 9), con
 * Gson real como `RetrofitClientFactory`: lo que puede fallar aquí es de cable
 * (ruta, campo ausente que Gson deja nulo, cuerpo que no es JSON).
 */
class V2VisitsApiObtenerVisitaTest {

    private lateinit var server: MockWebServer
    private lateinit var api: V2VisitsApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(V2VisitsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun responder(code: Int, body: String, contentType: String = "application/json") {
        server.enqueue(
            MockResponse().setResponseCode(
                code
            ).setHeader("Content-Type", contentType).setBody(body)
        )
    }

    @Test
    fun `imagen_ids presente se lee y la ruta lleva el id`() = runTest {
        responder(HttpURLConnection.HTTP_OK, """{"id":"x","cliente_id":7,"imagen_ids":["a"]}""")

        val dto = api.obtenerVisita("x")

        assertEquals("x", dto.id)
        assertEquals(7, dto.cliente_id)
        assertEquals(listOf("a"), dto.imagen_ids)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v2/visitas/x", request.path)
    }

    @Test
    fun `imagen_ids vacio se lee como lista vacia`() = runTest {
        responder(HttpURLConnection.HTTP_OK, """{"id":"x","imagen_ids":[]}""")
        assertEquals(emptyList<String>(), api.obtenerVisita("x").imagen_ids)
    }

    /** Servidor viejo: omite el campo. Gson no aplica defaults: queda nulo (Review Focus 4). */
    @Test
    fun `imagen_ids ausente queda nulo`() = runTest {
        responder(HttpURLConnection.HTTP_OK, """{"id":"x"}""")
        assertNull(api.obtenerVisita("x").imagen_ids)
    }

    /** Un `{}` de un proxy no trae id: el DTO no puede probar nada. */
    @Test
    fun `cuerpo sin id deja el id nulo`() = runTest {
        responder(HttpURLConnection.HTTP_OK, "{}")
        val dto = api.obtenerVisita("x")
        assertNull(dto.id)
        assertNull(dto.imagen_ids)
    }

    /** Review Focus 1: HTML 200 de un portal cautivo no produce un DTO con id. */
    @Test
    fun `HTML 200 lanza y no produce un DTO`() = runTest {
        responder(
            HttpURLConnection.HTTP_OK,
            "<html><body>iniciar sesion</body></html>",
            "text/html"
        )
        try {
            val dto = api.obtenerVisita("x")
            fail("un HTML 200 no debe producir un DTO: $dto")
        } catch (e: Exception) {
            assertTrue("se esperaba error de lectura, fue $e", e !is HttpException)
        }
    }

    @Test
    fun `404 llega como HttpException con su codigo`() = runTest {
        responder(
            HttpURLConnection.HTTP_NOT_FOUND,
            """{"status":404,"code":"visita_not_found"}""",
            "application/problem+json"
        )
        try {
            api.obtenerVisita("x")
            fail("se esperaba 404")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }
    }
}
