package com.example.msp_app.core.network.diagnostico

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Misma forma de cable que `V2PaymentsApi` (`:app`, vedado), con `Call` para leer todo. */
interface PagosDePruebaApi {
    @Multipart
    @POST("v2/cobranza/pagos")
    fun crearPago(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part("datos") datos: RequestBody
    ): Call<ResponseBody>

    @Multipart
    @POST("v2/cobranza/pagos")
    fun crearPagoSinClave(@Part("datos") datos: RequestBody): Call<ResponseBody>

    @GET("v2/cobranza/pagos/{id}")
    fun obtenerPago(@Path("id") id: String): Call<ResponseBody>

    @Multipart
    @POST("v2/visitas")
    fun crearVisita(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part("datos") datos: RequestBody
    ): Call<ResponseBody>
}

/**
 * E-APP-064: el interceptor observa y NO altera. Cada prueba compara lo que ve
 * Retrofit —y lo que recibe el servidor— con y sin interceptor, y con un
 * destino que revienta.
 */
class EnvioPagoDiagnosticoInterceptorTest {

    private lateinit var server: MockWebServer
    private val observados = CopyOnWriteArrayList<EnvioObservado>()
    private val grabadora = EnvioDiagnosticoSink { observados += it }
    private val queRevienta = EnvioDiagnosticoSink { throw IllegalStateException("sink roto") }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun datos() = """{"importe":1500}""".toRequestBody("application/json".toMediaType())

    private fun api(
        sink: EnvioDiagnosticoSink?,
        base: String = "/",
        readTimeoutMs: Long = 10_000
    ): PagosDePruebaApi {
        val cliente = OkHttpClient.Builder().readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        if (sink != null) cliente.addInterceptor(EnvioPagoDiagnosticoInterceptor(sink))
        return Retrofit.Builder()
            .baseUrl(server.url(base))
            .client(cliente.build())
            .build()
            .create(PagosDePruebaApi::class.java)
    }

    /** Lo que el worker de pagos puede leer de una respuesta, completo. */
    private data class Vista(
        val code: Int,
        val headers: Map<String, List<String>>,
        val intentCaptured: String?,
        val cuerpo: String
    )

    private fun vista(r: retrofit2.Response<ResponseBody>) = Vista(
        code = r.code(),
        headers = r.headers().toMultimap(),
        intentCaptured = r.headers()["X-Intent-Captured"],
        cuerpo = (if (r.isSuccessful) r.body() else r.errorBody())?.string().orEmpty()
    )

    /** Lo que llegó al servidor, completo. */
    private data class Recibida(
        val linea: String,
        val headers: Map<String, List<String>>,
        val cuerpo: String
    )

    private fun recibida(): Recibida {
        val req = server.takeRequest(5, TimeUnit.SECONDS)!!
        // El boundary del multipart es aleatorio por petición: se normaliza.
        val boundary = req.getHeader("Content-Type")!!.substringAfter("boundary=")
        return Recibida(
            linea = req.requestLine,
            headers = req.headers.toMultimap().mapValues { (_, v) ->
                v.map {
                    it.replace(
                        boundary,
                        "B"
                    )
                }
            },
            cuerpo = req.body.readUtf8().replace(boundary, "B")
        )
    }

    private fun respuestas(): List<() -> MockResponse> = listOf(
        { MockResponse().setResponseCode(200).setBody("""{"id":"pago-1","docto_cc_id":77}""") },
        {
            MockResponse().setResponseCode(422)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("X-Intent-Captured", "fi-123")
                .setBody(
                    """{"status":422,"code":"saldo_insuficiente","detail":"el saldo no alcanza"}"""
                )
        },
        {
            MockResponse().setResponseCode(422)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"status":422,"code":"validation_error"}""")
        },
        {
            MockResponse().setResponseCode(500)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(
                    """{"status":500,"code":"internal_error","detail":"ocurrió un error interno"}"""
                )
        },
        {
            MockResponse().setResponseCode(
                502
            ).setHeader("Content-Type", "text/html").setBody("<html>Bad Gateway</html>")
        },
        {
            // Más grande que MAX_PEEK_BYTES: el resto debe seguir llegando a Retrofit.
            MockResponse().setResponseCode(422)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("X-Intent-Captured", "fi-grande")
                .setBody("""{"code":"grande","detail":"${"x".repeat(20_000)}"}""")
        }
    )

    private fun correr(sink: EnvioDiagnosticoSink?, mock: MockResponse): Pair<Vista, Recibida> {
        server.enqueue(mock)
        val r = api(sink).crearPago("pago-1", datos()).execute()
        return vista(r) to recibida()
    }

    @Test
    fun `E-APP-064 la respuesta y la peticion son identicas con y sin interceptor`() {
        respuestas().forEach { mock ->
            val sin = correr(null, mock())
            val con = correr(grabadora, mock())
            val roto = correr(queRevienta, mock())
            assertEquals("con interceptor (${sin.first.code})", sin, con)
            assertEquals("con sink que revienta (${sin.first.code})", sin, roto)
        }
        // Control positivo: el interceptor sí estaba observando.
        assertEquals(respuestas().size, observados.size)
    }

    @Test
    fun `E-APP-064 el cuerpo grande llega completo y X-Intent-Captured sigue legible`() {
        val (vista, _) = correr(grabadora, respuestas().last()())
        assertEquals("fi-grande", vista.intentCaptured)
        assertTrue("cuerpo completo, no solo el vistazo", vista.cuerpo.length > 20_000)
        assertEquals("grande", observados.single().fallo!!.codigo)
    }

    @Test
    fun `E-APP-064 devuelve el MISMO objeto Response y re-lanza la MISMA excepcion`() {
        var deAdentro: Response? = null
        var deAfuera: Response? = null
        val boom = IOException("boom")
        var lanzar = false
        val adentro = Interceptor { chain ->
            if (lanzar) throw boom
            chain.proceed(chain.request()).also { deAdentro = it }
        }
        val afuera = Interceptor { chain -> chain.proceed(chain.request()).also { deAfuera = it } }
        listOf(grabadora, queRevienta).forEach { sink ->
            val cliente = OkHttpClient.Builder()
                .addInterceptor(afuera)
                .addInterceptor(EnvioPagoDiagnosticoInterceptor(sink))
                .addInterceptor(adentro)
                .build()
            val api = Retrofit.Builder().baseUrl(server.url("/")).client(cliente).build()
                .create(PagosDePruebaApi::class.java)

            lanzar = false
            server.enqueue(MockResponse().setResponseCode(422).setBody("""{"code":"x"}"""))
            api.crearPago("pago-1", datos()).execute()
            assertSame(deAdentro, deAfuera)

            lanzar = true
            try {
                api.crearPago("pago-1", datos()).execute()
                fail("debió lanzar")
            } catch (e: IOException) {
                assertSame(boom, e)
            }
        }
        assertEquals("una sola petición real por envío", 2, server.requestCount)
    }

    @Test
    fun `observa 2xx como exito y 4xx con el code del servidor`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        server.enqueue(
            MockResponse().setResponseCode(
                422
            ).setBody("""{"code":"saldo_insuficiente","detail":"el saldo no alcanza"}""")
        )
        api(grabadora).crearPago("pago-ok", datos()).execute()
        api(grabadora).crearPago("pago-mal", datos()).execute()

        assertEquals(EnvioObservado("pago", "pago-ok", null), observados[0])
        assertEquals(
            EnvioObservado(
                "pago",
                "pago-mal",
                FalloDeEnvio("saldo_insuficiente", "El saldo no alcanza", 422)
            ),
            observados[1]
        )
    }

    @Test
    fun `fallo de red y tiempo agotado se anotan con http 0 y se re-lanzan`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            api(grabadora).crearPago("pago-red", datos()).execute()
            fail("debió lanzar")
        } catch (_: IOException) {
        }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try {
            api(grabadora, readTimeoutMs = 300).crearPago("pago-lento", datos()).execute()
            fail("debió lanzar")
        } catch (_: IOException) {
        }
        assertEquals("pago-red", observados[0].filaId)
        assertEquals("network_error", observados[0].fallo!!.codigo)
        assertEquals(0, observados[0].fallo!!.http)
        assertEquals("pago-lento", observados[1].filaId)
        assertEquals("timeout", observados[1].fallo!!.codigo)
    }

    @Test
    fun `solo observa POST de pagos con Idempotency-Key`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        api(grabadora).obtenerPago("pago-1").execute()
        api(grabadora).crearPagoSinClave(datos()).execute()
        api(grabadora).crearVisita("visita-1", datos()).execute()
        assertTrue("nada fuera de POST /v2/cobranza/pagos con clave", observados.isEmpty())
    }

    @Test
    fun `observa tambien con baseURL con prefijo de ruta`() {
        server.enqueue(MockResponse().setResponseCode(503))
        api(grabadora, base = "/api/").crearPago("pago-1", datos()).execute()
        assertEquals("http_503", observados.single().fallo!!.codigo)
        assertEquals("/api/v2/cobranza/pagos", server.takeRequest().path)
    }

    @Test
    fun `el exito no lee el cuerpo`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"pago-1"}"""))
        val r = api(grabadora).crearPago("pago-1", datos()).execute()
        assertNull(observados.single().fallo)
        assertEquals("""{"id":"pago-1"}""", r.body()!!.string())
    }
}
