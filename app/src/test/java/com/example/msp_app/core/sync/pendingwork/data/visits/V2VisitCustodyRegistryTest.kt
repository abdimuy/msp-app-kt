package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import java.io.IOException
import java.net.HttpURLConnection
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The wire contract of `GET /v2/visitas/by-ids`, against a real HTTP server.
 *
 * `MockWebServer` and not a fake service, because the things that can go wrong
 * here are wire-shaped: whether the ids travel as ONE comma-separated `ids`
 * parameter (the server splits on commas — repeated `ids=` would be read as a
 * single id and silently confirm nothing), whether a zone leaks into the query
 * (Ruling B says there is none), and whether a bare JSON array deserializes.
 */
class V2VisitCustodyRegistryTest {

    private lateinit var server: MockWebServer
    private lateinit var registry: V2VisitCustodyRegistry

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(V2VisitsApi::class.java)
        registry = V2VisitCustodyRegistry(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private fun ids(count: Int) = List(count) { "id-${it + 1}" }

    @Test
    fun `los ids viajan como UN solo parametro ids separado por comas`() = runTest {
        enqueueJson("[]")

        registry.findExisting(listOf("a", "b", "c"))

        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertEquals("/v2/visitas/by-ids", url.encodedPath)
        assertEquals(listOf("a,b,c"), url.queryParameterValues("ids"))
        assertEquals("GET", request.method)
    }

    @Test
    fun `la peticion NO lleva zona_id (Ruling B)`() = runTest {
        enqueueJson("[]")

        registry.findExisting(listOf("a"))

        val url = server.takeRequest().requestUrl!!
        assertNull(url.queryParameter("zona_id"))
        assertNull(url.queryParameter("zona"))
        assertEquals(setOf("ids"), url.queryParameterNames)
    }

    @Test
    fun `un arreglo JSON plano se lee como la lista de ids confirmados`() = runTest {
        enqueueJson("""["a","c"]""")

        val existing = registry.findExisting(listOf("a", "b", "c"))

        assertEquals(listOf("a", "c"), existing)
    }

    @Test
    fun `un arreglo vacio significa que el servidor no conoce ninguno`() = runTest {
        enqueueJson("[]")

        assertEquals(emptyList<String>(), registry.findExisting(listOf("a", "b")))
    }

    @Test
    fun `justo en el tope (100) la peticion se envia con los 100 ids`() = runTest {
        enqueueJson("[]")
        val batch = ids(VisitCustodyRegistry.MAX_IDS_PER_REQUEST)

        registry.findExisting(batch)

        val sent = server.takeRequest().requestUrl!!.queryParameter("ids")!!
        assertEquals(100, sent.split(",").size)
        assertEquals(batch, sent.split(","))
    }

    @Test
    fun `sobre el tope (101) falla localmente y NO llega a salir a la red`() = runTest {
        val error = runCatching { registry.findExisting(ids(101)) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("ids_too_many"))
        // Ninguna peticion salio: el 422 del servidor nunca se provoca.
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `una lista vacia falla localmente y NO llega a salir a la red`() = runTest {
        val error = runCatching { registry.findExisting(emptyList()) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("ids_required"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `un error de red se propaga tal cual, no se convierte en lista vacia`() = runTest {
        server.shutdown() // servidor caido: la conexion falla

        val error = runCatching { registry.findExisting(listOf("a")) }.exceptionOrNull()

        // Lo que NUNCA debe pasar: devolver emptyList() y que el reconciliador
        // lea "el servidor no tiene ninguna" en lugar de "no se sabe".
        assertTrue("un fallo de transporte debe lanzar, no responder vacio", error is IOException)
    }

    @Test
    fun `un 5xx se propaga como excepcion, no como respuesta vacia`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR))

        val error = runCatching { registry.findExisting(listOf("a")) }.exceptionOrNull()

        assertTrue(error is retrofit2.HttpException)
        assertEquals(500, (error as retrofit2.HttpException).code())
    }
}
