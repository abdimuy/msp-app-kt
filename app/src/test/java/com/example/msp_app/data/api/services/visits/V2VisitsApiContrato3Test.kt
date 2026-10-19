package com.example.msp_app.data.api.services.visits

import com.example.msp_app.core.common.visitas.envio.CompromisoLocal
import com.example.msp_app.workers.consultarVisitaEnServidor
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Cable del contrato de visitas 3 (plan visitas P2, Tarea 15; msp-api
 * `dto.go` de `feat/visitas-p2-t10`): los cinco campos en la lectura y
 * `by-ids?detalle=1`. Gson real: un campo ausente queda nulo, nunca default.
 */
class V2VisitsApiContrato3Test {

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

    private fun responder(body: String) = server.enqueue(
        MockResponse().setResponseCode(
            200
        ).setHeader("Content-Type", "application/json").setBody(body)
    )

    private val completo = CompromisoLocal(13914738, "2026-10-20", 15000L, "2026-10-21", "16:30")

    @Test
    fun `GET con los cinco campos los lee como compromiso`() = runTest {
        responder(
            """{"id":"x","imagen_ids":[],"updated_at":"2026-10-19T15:00:00.123456789Z",""" +
                """"promesa_venta_id":13914738,"promesa_fecha":"2026-10-20","promesa_monto_centavos":15000,""" +
                """"cita_fecha":"2026-10-21","cita_hora":"16:30"}"""
        )
        val get = consultarVisitaEnServidor(api, "x")
        assertEquals(200, get.codigo)
        assertEquals(completo, get.visita?.compromiso)
    }

    /** Servidor viejo (claves ausentes) y fila sin bloques (`null`): igual, "no lo dijo". */
    @Test
    fun `GET sin bloques deja el compromiso nulo`() = runTest {
        responder("""{"id":"x","imagen_ids":[]}""")
        assertNull(consultarVisitaEnServidor(api, "x").visita?.compromiso)
        responder(
            """{"id":"x","imagen_ids":[],"promesa_venta_id":null,"promesa_fecha":null,""" +
                """"promesa_monto_centavos":null,"cita_fecha":null,"cita_hora":null}"""
        )
        assertNull(consultarVisitaEnServidor(api, "x").visita?.compromiso)
    }

    @Test
    fun `by-ids con detalle manda detalle 1 y lee los bloques con nulos`() = runTest {
        responder(
            """[{"id":"a","promesa_venta_id":null,"promesa_fecha":"2026-10-20","promesa_monto_centavos":null,""" +
                """"cita_fecha":null,"cita_hora":null,"imagen_ids":["f1"]},{"id":"b"}]"""
        )

        val r = api.visitasExistentesPorIdsDetalle("a,b", V2VisitsApi.DETALLE_SI)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/v2/visitas/by-ids", url.encodedPath)
        assertEquals("a,b", url.queryParameter("ids"))
        assertEquals("1", url.queryParameter("detalle"))
        assertEquals("2026-10-20", r[0].promesa_fecha)
        assertNull(r[0].promesa_venta_id)
        assertEquals(listOf("f1"), r[0].imagen_ids)
        assertNull("Gson no aplica defaults: ausente es nulo", r[1].imagen_ids)
    }

    @Test
    fun `respuesta del POST con bloques`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(
                201
            ).setHeader("Content-Type", "application/json").setBody(
                """{"id":"x","promesa_venta_id":13914738,"promesa_fecha":"2026-10-20",""" +
                    """"promesa_monto_centavos":15000,"cita_fecha":"2026-10-21","cita_hora":"16:30"}"""
            )
        )
        val dto = api.crearVisita(
            "x",
            CrearVisitaBody(
                id = "x", cliente_id = 1, cobrador_id = 2, cobrador = "Ramirez Ortiz, Fernando",
                forma_cobro_id = 0, lat = 0.0, lng = 0.0, tipo_visita = "PROMESA",
                zona_cliente_id = 21552, fecha = "2026-10-19T15:00:00Z"
            )
        )
        assertEquals(completo, dto.compromiso())
    }
}
