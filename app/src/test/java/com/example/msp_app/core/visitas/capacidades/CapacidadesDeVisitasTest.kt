package com.example.msp_app.core.visitas.capacidades

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * E-APP-097 (plan visitas P2, Tarea 15; spec visitas §5.2.1): la negociación
 * del contrato de visitas. Retrofit y Gson reales contra MockWebServer, como
 * `RetrofitClientFactory`: lo que puede fallar es de cable.
 */
class CapacidadesDeVisitasTest : RobolectricTestBase() {

    private lateinit var server: MockWebServer
    private lateinit var api: V2VisitsApi
    private val clock = FakeClock(Instant.parse("2026-10-19T15:00:00Z"))
    private lateinit var capacidades: CapacidadesDeVisitas

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(V2VisitsApi::class.java)
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(
            CapacidadesDeVisitas.PREFS,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        capacidades = CapacidadesDeVisitas.de(context, clock)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun responder(code: Int, body: String, tipo: String = "application/json") =
        server.enqueue(
            MockResponse().setResponseCode(code).setHeader("Content-Type", tipo).setBody(body)
        )

    private fun refrescar() = runBlocking { capacidades.refrescar(api) }

    private fun negociarContrato3() {
        responder(200, """{"contrato":3,"compromisos":true}""")
        refrescar()
        assertEquals(
            "control: la vigente es contrato 3",
            Capacidades(3, true),
            capacidades.actual()
        )
    }

    @Test
    fun `E-APP-097 sin negociar es contrato 2 y no manda compromisos`() {
        assertEquals(Capacidades.CONTRATO_2, capacidades.actual())
        assertFalse(capacidades.actual().mandaCompromisos())
        assertTrue(capacidades.necesitaRefresco())
    }

    @Test
    fun `E-APP-097 200 con contrato 3 y compromisos se guarda y manda`() {
        responder(200, """{"contrato":3,"compromisos":true}""")

        assertEquals(Capacidades(3, true), refrescar())

        assertTrue(capacidades.actual().mandaCompromisos())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v2/visitas/capacidades", request.path)
    }

    @Test
    fun `contrato 3 con la bandera apagada no manda compromisos`() {
        responder(200, """{"contrato":3,"compromisos":false}""")
        refrescar()
        assertEquals(Capacidades(3, false), capacidades.actual())
        assertFalse(capacidades.actual().mandaCompromisos())
    }

    @Test
    fun `compromisos sin contrato 3 no manda`() {
        assertFalse(Capacidades(2, true).mandaCompromisos())
        assertTrue(Capacidades(4, true).mandaCompromisos())
    }

    /** API vieja: la ruta no existe. Aunque la vigente fuera contrato 3, se baja a 2. */
    @Test
    fun `E-APP-097 404 es contrato 2 aunque antes fuera 3`() {
        negociarContrato3()
        clock.advance(Duration.ofHours(7))
        responder(404, """{"title":"Not Found"}""")

        assertEquals(Capacidades.CONTRATO_2, refrescar())

        assertFalse(capacidades.actual().mandaCompromisos())
        assertFalse("un 404 es respuesta: cuenta como refresco", capacidades.necesitaRefresco())
    }

    @Test
    fun `E-APP-097 capacidadesErrorConservaLaVigente`() {
        negociarContrato3()
        val errores = listOf<() -> Unit>(
            { responder(500, """{"title":"boom"}""") },
            { responder(401, """{"title":"no"}""") },
            { responder(503, "<html>mantenimiento</html>", "text/html") },
            // Un portal cautivo: 200 con HTML.
            { responder(200, "<html>inicia sesion</html>", "text/html") },
            // Un proxy: 200 sin las claves. Gson las deja nulas: no prueba nada.
            { responder(200, "{}") },
            { responder(200, """{"contrato":3}""") },
            { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        )
        clock.advance(Duration.ofHours(7))
        errores.forEachIndexed { i, encolar ->
            encolar()
            assertEquals("error #$i", Capacidades(3, true), refrescar())
            assertEquals("error #$i", Capacidades(3, true), capacidades.actual())
            assertTrue("error #$i: no cuenta como refresco", capacidades.necesitaRefresco())
        }
    }

    @Test
    fun `necesitaRefresco a las 6 horas y si el reloj retrocede`() {
        negociarContrato3()
        assertFalse(capacidades.necesitaRefresco())
        assertFalse(
            capacidades.necesitaRefresco(clock.now().plus(Duration.ofHours(6)).minusSeconds(1))
        )
        assertTrue(capacidades.necesitaRefresco(clock.now().plus(Duration.ofHours(6))))
        assertTrue(capacidades.necesitaRefresco(clock.now().minusSeconds(1)))
    }

    @Test
    fun `refrescarSiToca no pregunta antes de 6 horas`() {
        negociarContrato3()
        var creados = 0
        runBlocking {
            capacidades.refrescarSiToca {
                creados++
                api
            }
        }
        assertEquals("ni siquiera crea el servicio", 0, creados)
        assertEquals(1, server.requestCount)

        clock.advance(Duration.ofHours(6))
        responder(200, """{"contrato":3,"compromisos":false}""")
        runBlocking {
            capacidades.refrescarSiToca {
                creados++
                api
            }
        }
        assertEquals(1, creados)
        assertEquals(Capacidades(3, false), capacidades.actual())
    }
}
