package com.example.msp_app.workers

import com.example.msp_app.core.common.visitas.envio.ResultadoGet
import com.example.msp_app.core.common.visitas.envio.VisitaEnServidor
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDetalleDTO
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * El mapeo del `GET /v2/visitas/{id}` a [ResultadoGet] (opción A del dueño,
 * 2026-10-17; E-VIS-022). Fijado aquí porque el worker no lo distingue por su
 * salida (NO_EXISTE e INDETERMINADA reintentan igual):
 *  - `HttpException` → `ResultadoGet(código, null)`;
 *  - cualquier otra excepción (HTML 200, red, cuerpo vacío, Gson) →
 *    `ResultadoGet(null, null)`; nunca un DTO armado a mano;
 *  - la cancelación se propaga.
 */
class ConsultaDeVisitaEnServidorTest {

    private val base: V2VisitsApi = fakeV2Api { _, _ -> error("no") }

    private fun apiQueLanza(e: Throwable) = object : V2VisitsApi by base {
        override suspend fun obtenerVisita(id: String): VisitaDetalleDTO = throw e
    }

    @Test
    fun `HttpException da su codigo y sin visita`() = runBlocking {
        listOf(404, 401, 409, 500, 503).forEach { code ->
            assertEquals(
                ResultadoGet(code, null),
                consultarVisitaEnServidor(apiQueLanza(httpError(code)), "v-1")
            )
        }
    }

    @Test
    fun `cualquier otra excepcion es sin codigo y sin visita`() = runBlocking {
        listOf(
            IOException("red"),
            IllegalStateException("json roto"),
            com.google.gson.JsonSyntaxException("html"),
            KotlinNullPointerException("cuerpo nulo"),
            NullPointerException("cuerpo nulo")
        ).forEach { e ->
            assertEquals(
                e.toString(),
                ResultadoGet(null, null),
                consultarVisitaEnServidor(apiQueLanza(e), "v-1")
            )
        }
    }

    @Test
    fun `la cancelacion se propaga`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                consultarVisitaEnServidor(
                    apiQueLanza(CancellationException("detenido")),
                    "v-1"
                )
            }
        }
    }

    // ─── con el Retrofit real ────────────────────────────────────────────────

    private fun conServidor(
        vararg respuestas: MockResponse,
        verificarRuta: Boolean = true,
        bloque: suspend (V2VisitsApi) -> Unit
    ) {
        val server = MockWebServer()
        respuestas.forEach { server.enqueue(it) }
        server.start()
        try {
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .client(OkHttpClient.Builder().readTimeout(400, TimeUnit.MILLISECONDS).build())
                .build()
                .create(V2VisitsApi::class.java)
            runBlocking { bloque(api) }
            if (verificarRuta) {
                assertEquals(
                    "/v2/visitas/v-1",
                    server.takeRequest(1, TimeUnit.SECONDS)?.path
                )
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `200 con JSON da la visita del cuerpo`() = conServidor(
        MockResponse().setResponseCode(200).setBody("""{"id":"v-1","imagen_ids":["a","b"]}""")
    ) { api ->
        assertEquals(
            ResultadoGet(200, VisitaEnServidor("v-1", listOf("a", "b"))),
            consultarVisitaEnServidor(api, "v-1")
        )
    }

    @Test
    fun `servidor viejo sin imagen_ids queda nulo`() = conServidor(
        MockResponse().setResponseCode(200).setBody("""{"id":"v-1"}""")
    ) { api ->
        assertEquals(
            ResultadoGet(200, VisitaEnServidor("v-1", null)),
            consultarVisitaEnServidor(api, "v-1")
        )
    }

    @Test
    fun `HTML 200 de un portal cautivo es sin codigo`() = conServidor(
        MockResponse().setResponseCode(
            200
        ).setHeader("Content-Type", "text/html").setBody("<html>wifi</html>")
    ) { api ->
        assertEquals(ResultadoGet(null, null), consultarVisitaEnServidor(api, "v-1"))
    }

    @Test
    fun `200 sin cuerpo es sin codigo`() = conServidor(MockResponse().setResponseCode(200)) { api ->
        assertEquals(ResultadoGet(null, null), consultarVisitaEnServidor(api, "v-1"))
    }

    @Test
    fun `204 es sin codigo`() = conServidor(MockResponse().setResponseCode(204)) { api ->
        assertEquals(ResultadoGet(null, null), consultarVisitaEnServidor(api, "v-1"))
    }

    @Test
    fun `404 real da 404`() = conServidor(
        MockResponse().setResponseCode(404).setBody("""{"code":"not_found"}""")
    ) { api ->
        assertEquals(ResultadoGet(404, null), consultarVisitaEnServidor(api, "v-1"))
    }

    @Test
    fun `corte de red es sin codigo`() = conServidor(
        MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
        verificarRuta = false
    ) { api ->
        assertEquals(ResultadoGet(null, null), consultarVisitaEnServidor(api, "v-1"))
    }
}
