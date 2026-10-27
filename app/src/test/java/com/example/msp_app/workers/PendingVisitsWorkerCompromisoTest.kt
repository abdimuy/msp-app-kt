package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import com.example.msp_app.core.network.AuthTokenProvider
import com.example.msp_app.core.network.NetworkConfig
import com.example.msp_app.core.network.RetrofitClientFactory
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.upload.HEADER_INTENT_CAPTURED
import com.example.msp_app.core.visitas.capacidades.Capacidades
import com.example.msp_app.core.visitas.capacidades.CapacidadesDeVisitas
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.toCrearVisitaBody
import com.google.gson.JsonParser
import java.time.Instant
import javax.inject.Provider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contrato de visitas 3 en el envío (plan visitas P2, Tarea 15; spec visitas
 * §5.2.1, §5.2.5, §4.2-3/-5). El worker REAL con el cliente v2 REAL de
 * `RetrofitClientFactory` (Gson incluido) contra MockWebServer, y Room en
 * memoria.
 *
 *  - E-APP-097: los cinco campos viajan SOLO con `contrato >= 3 &&
 *    compromisos`; contra un API sin capacidades (404) el cuerpo es el de hoy y
 *    el compromiso queda por rellenar, no se pierde.
 *  - E-APP-098: la prueba del compromiso es aparte de la de la visita; el
 *    relleno escribe solo sus marcas y nunca desconfirma la visita base.
 */
class PendingVisitsWorkerCompromisoTest : RoomTestBase() {

    private val ahora = Instant.parse("2026-10-19T15:00:00Z")
    private val clock = FakeClock(ahora)
    private val id = "visita-001"
    private val confirmadaAntes = "2026-10-18T20:00:00Z"

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var api: V2VisitsApi

    /** Respuestas por ruta; lo no previsto es 599 para que se note. */
    private var alCapacidades: () -> MockResponse = { json(404, """{"title":"Not Found"}""") }
    private var alPost: (RecordedRequest) -> MockResponse = { json(599, "{}") }
    private var alGet: () -> MockResponse = { json(599, "{}") }
    private val posts = mutableListOf<String>()

    private object TokenFijo : AuthTokenProvider {
        override suspend fun token(forceRefresh: Boolean): String = "tok-t15"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(
            CapacidadesDeVisitas.PREFS,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/v2/visitas/capacidades" -> alCapacidades()
                request.method == "POST" && request.path == "/v2/visitas" -> {
                    posts += request.body.readUtf8()
                    alPost(request)
                }
                request.method == "GET" && request.path == "/v2/visitas/$id" -> alGet()
                else -> json(599, """{"inesperada":"${request.method} ${request.path}"}""")
            }
        }
        server.start()
        val base = server.url("/").toString()
        api = RetrofitClientFactory(Provider { NetworkConfig(base, base, base, "2.20.0") }, TokenFijo)
            .v2()
            .create(V2VisitsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ─── fixtures ──────────────────────────────────────────────────────────────

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(
        code
    ).setHeader("Content-Type", "application/json").setBody(body)

    private fun conPromesa() = pendingVisit(id).copy(
        PROMESA_VENTA_ID = 13914738,
        PROMESA_FECHA = "2026-10-20",
        PROMESA_MONTO_CENTAVOS = 15000L,
        CITA_FECHA = "2026-10-21",
        CITA_HORA = "16:30"
    )

    private val bloques = """"promesa_venta_id":13914738,"promesa_fecha":"2026-10-20",""" +
        """"promesa_monto_centavos":15000,"cita_fecha":"2026-10-21","cita_hora":"16:30""""

    private val bloquesNulos = """"promesa_venta_id":null,"promesa_fecha":null,""" +
        """"promesa_monto_centavos":null,"cita_fecha":null,"cita_hora":null"""

    private fun eco(conBloques: String = bloques) = json(201, """{"id":"$id",$conBloques}""")

    private fun detalle(conBloques: String) =
        json(200, """{"id":"$id","imagen_ids":[],"updated_at":null,$conBloques}""")

    private fun conCustodia(code: Int, codigo: String) = json(
        code,
        """{"status":$code,"code":"$codigo","detail":"esta visita ya tiene otro compromiso registrado"}"""
    ).setHeader(HEADER_INTENT_CAPTURED, "c0ffee00-0000-4000-8000-000000000000")

    private suspend fun fila() = db.visitDao().findVisitById(id)!!

    private fun correr(capacidades: Capacidades): Result = runPendingVisitsWorker(
        context,
        db.visitImageDao(),
        clock,
        ModoEnvioVisitas.V3,
        visitId = id,
        api = api,
        capacidades = capacidades
    )

    /** La capacidad que deja la negociación REAL contra este servidor. */
    private fun negociar(): Capacidades = runBlocking {
        CapacidadesDeVisitas.de(context, clock).refrescar(api)
    }

    private fun claves(cuerpo: String): Set<String> = JsonParser.parseString(
        cuerpo
    ).asJsonObject.keySet()

    // ─── E-APP-097: negociación y cuerpo ───────────────────────────────────────

    @Test
    fun `E-APP-097 apkNuevoContraApiViejaNoMandaCampos`() = runBlocking {
        db.visitDao().insertVisit(conPromesa())
        alCapacidades = { json(404, """{"title":"Not Found"}""") }
        alPost = { eco(conBloques = """"cliente_id":11486""") }

        val capacidades = negociar()
        assertEquals(Capacidades.CONTRATO_2, capacidades)
        assertEquals(Result.success(), correr(capacidades))

        val cuerpo = posts.single()
        CAMPOS.forEach { assertFalse("el cuerpo no lleva $it: $cuerpo", it in claves(cuerpo)) }
        // Compatibilidad: la visita base se confirma y el compromiso queda
        // en Room, por rellenar cuando el servidor lo acepte. No se pierde.
        val v = fila()
        assertNotNull(v.CONFIRMADA_EN)
        assertNull(v.COMPROMISO_CONFIRMADO_EN)
        assertEquals("2026-10-20", v.PROMESA_FECHA)
        assertEquals(listOf(id), db.visitDao().compromisosPorRellenar())
    }

    @Test
    fun `E-APP-097 conCapacidadMandaLosCinco y el eco confirma visita y compromiso`() =
        runBlocking {
            db.visitDao().insertVisit(conPromesa())
            alCapacidades = { json(200, """{"contrato":3,"compromisos":true}""") }
            alPost = { eco() }

            assertEquals(Result.success(), correr(negociar()))

            val cuerpo = JsonParser.parseString(posts.single()).asJsonObject
            assertEquals(13914738, cuerpo["promesa_venta_id"].asInt)
            assertEquals("2026-10-20", cuerpo["promesa_fecha"].asString)
            assertEquals(15000L, cuerpo["promesa_monto_centavos"].asLong)
            assertEquals("2026-10-21", cuerpo["cita_fecha"].asString)
            assertEquals("16:30", cuerpo["cita_hora"].asString)
            val v = fila()
            assertEquals(ahora.toString(), v.CONFIRMADA_EN)
            assertEquals(ahora.toString(), v.COMPROMISO_CONFIRMADO_EN)
            assertEquals(emptyList<String>(), db.visitDao().compromisosPorRellenar())
        }

    /** Contrato 3 con la bandera apagada: el cuerpo de hoy. */
    @Test
    fun `E-APP-097 contrato 3 sin la bandera no manda campos`() = runBlocking {
        db.visitDao().insertVisit(conPromesa())
        alCapacidades = { json(200, """{"contrato":3,"compromisos":false}""") }
        alPost = { eco(conBloques = bloquesNulos) }

        assertEquals(Result.success(), correr(negociar()))

        CAMPOS.forEach { assertFalse(it in claves(posts.single())) }
        assertNull(fila().COMPROMISO_CONFIRMADO_EN)
    }

    /**
     * Gson del cliente v2 (`GsonConverterFactory.create()`, sin
     * `serializeNulls`): un campo nulo NO viaja, ni como `null`. Si alguien
     * activara `serializeNulls`, un servidor de contrato 2 vería cinco claves
     * desconocidas y respondería 422 a todas las visitas.
     */
    @Test
    fun `E-APP-097 gsonNoSerializaNulos`() = runBlocking {
        alPost = { eco(conBloques = bloquesNulos) }

        api.crearVisita(id, conPromesa().toCrearVisitaBody(conCompromisos = false))
        api.crearVisita(
            id,
            pendingVisit(id).copy(NOTA = null, IMPTE_DOCTO_CC_ID = 0).toCrearVisitaBody(true)
        )

        posts.forEach { cuerpo ->
            CAMPOS.forEach { assertFalse("$it en $cuerpo", it in claves(cuerpo)) }
            assertFalse("ningún null en el cable: $cuerpo", cuerpo.contains("null"))
        }
        // Control positivo: con valores sí viajan.
        api.crearVisita(id, conPromesa().toCrearVisitaBody(conCompromisos = true))
        assertTrue(claves(posts.last()).containsAll(CAMPOS))
    }

    // ─── E-APP-098: existencia con compromiso ─────────────────────────────────

    /** Spec §4.2-5: el servidor tiene la visita pero sin el compromiso: no se suelta. */
    @Test
    fun `E-APP-098 existenciaSinCompromisoNoSuelta`() = runBlocking {
        db.visitDao().insertVisit(conPromesa())
        alPost = { json(500, """{"title":"boom"}""") }
        alGet = { detalle(bloquesNulos) }

        assertEquals(Result.retry(), correr(Capacidades(3, true)))

        val v = fila()
        assertNull(v.CONFIRMADA_EN)
        assertEquals(0, v.GUARDADO_EN_MICROSIP)
        assertNull(v.COMPROMISO_CONFIRMADO_EN)

        // Control positivo: con el compromiso igual en el GET, sí se suelta.
        alGet = { detalle(bloques) }
        assertEquals(Result.success(), correr(Capacidades(3, true)))
        assertNotNull(fila().CONFIRMADA_EN)
        assertNotNull(fila().COMPROMISO_CONFIRMADO_EN)
    }

    /** Sin compromiso enviado (contrato 2), la existencia es la de antes. */
    @Test
    fun `sin capacidad la existencia no exige el compromiso`() = runBlocking {
        db.visitDao().insertVisit(conPromesa())
        alPost = { json(500, """{"title":"boom"}""") }
        alGet = { detalle(bloquesNulos) }

        assertEquals(Result.success(), correr(Capacidades.CONTRATO_2))

        assertNotNull(fila().CONFIRMADA_EN)
        assertNull(fila().COMPROMISO_CONFIRMADO_EN)
    }

    @Test
    fun `respuesta 2xx sin los bloques confirma la base y deja el compromiso por rellenar`() =
        runBlocking {
            db.visitDao().insertVisit(conPromesa())
            alPost = { eco(conBloques = bloquesNulos) }

            assertEquals(Result.success(), correr(Capacidades(3, true)))

            assertNotNull(fila().CONFIRMADA_EN)
            assertNull(fila().COMPROMISO_CONFIRMADO_EN)
            assertEquals(listOf(id), db.visitDao().compromisosPorRellenar())
        }

    /**
     * Primer envío de una visita que el servidor ya tiene con OTRO compromiso
     * (E-VIS-047): 422 con custodia. La visita base está en MSP_VISITAS, no en
     * la bandeja: se confirma la base y se resguarda solo el compromiso.
     */
    @Test
    fun `E-APP-098 primer envio con compromiso distinto confirma la base y resguarda el compromiso`() =
        runBlocking {
            db.visitDao().insertVisit(conPromesa())
            alPost = { conCustodia(422, "visita_compromiso_distinto") }
            alGet = { detalle(bloques.replace("2026-10-20", "2026-10-22")) }

            assertEquals(Result.success(), correr(Capacidades(3, true)))

            val v = fila()
            assertEquals(ahora.toString(), v.CONFIRMADA_EN)
            assertNull(v.RESGUARDADA_EN)
            assertEquals(ahora.toString(), v.COMPROMISO_RESGUARDADO_EN)
            assertNull(v.COMPROMISO_CONFIRMADO_EN)
        }

    /** Control del anterior: si el servidor NO tiene la visita, la bandeja tiene todo. */
    @Test
    fun `422 con custodia sin la visita en el servidor resguarda la visita entera`() = runBlocking {
        db.visitDao().insertVisit(conPromesa())
        alPost = { conCustodia(422, "visita_promesa_monto_invalido") }
        alGet = { json(404, """{"title":"Not Found"}""") }

        assertEquals(Result.success(), correr(Capacidades(3, true)))

        val v = fila()
        assertNull(v.CONFIRMADA_EN)
        assertEquals(ahora.toString(), v.RESGUARDADA_EN)
        assertNull(v.COMPROMISO_RESGUARDADO_EN)
    }

    // ─── E-APP-098: relleno ──────────────────────────────────────────────────

    private suspend fun sembrarConfirmada() {
        db.visitDao().insertVisit(conPromesa())
        db.visitDao().confirmarTrasSubida(id, confirmadaAntes)
    }

    @Test
    fun `E-APP-098 rellenoConfirmaSoloElCompromiso`() = runBlocking {
        sembrarConfirmada()
        val antes = fila()
        alPost = { eco() }

        assertEquals(Result.success(), correr(Capacidades(3, true)))

        val cuerpo = JsonParser.parseString(posts.single()).asJsonObject
        assertEquals("mismo id", id, cuerpo["id"].asString)
        assertEquals("2026-10-20", cuerpo["promesa_fecha"].asString)
        assertEquals(antes.copy(COMPROMISO_CONFIRMADO_EN = ahora.toString()), fila())
    }

    @Test
    fun `E-APP-098 rellenoDistintoConCustodiaSeResguardaSinDesconfirmarLaVisita`() = runBlocking {
        sembrarConfirmada()
        val antes = fila()
        alPost = { conCustodia(422, "visita_compromiso_distinto") }
        alGet = { detalle(bloques.replace("16:30", "17:00")) }

        assertEquals(Result.success(), correr(Capacidades(3, true)))

        assertEquals(antes.copy(COMPROMISO_RESGUARDADO_EN = ahora.toString()), fila())
        assertEquals(emptyList<String>(), db.visitDao().compromisosPorRellenar())
    }

    @Test
    fun `relleno distinto sin custodia reintenta y no marca nada`() = runBlocking {
        sembrarConfirmada()
        val antes = fila()
        alPost = { json(422, """{"code":"visita_compromiso_distinto"}""") }
        alGet = { detalle(bloques.replace("16:30", "17:00")) }

        assertEquals(Result.retry(), correr(Capacidades(3, true)))

        assertEquals(antes, fila())
    }

    @Test
    fun `relleno con 2xx sin los bloques reintenta salvo que el GET los pruebe`() = runBlocking {
        sembrarConfirmada()
        val antes = fila()
        alPost = { eco(conBloques = bloquesNulos) }
        alGet = { detalle(bloquesNulos) }

        assertEquals(Result.retry(), correr(Capacidades(3, true)))
        assertEquals(antes, fila())

        alGet = { detalle(bloques) }
        assertEquals(Result.success(), correr(Capacidades(3, true)))
        assertEquals(antes.copy(COMPROMISO_CONFIRMADO_EN = ahora.toString()), fila())
    }

    /** Sin capacidad, una confirmada no se reenvía: el relleno espera al contrato 3. */
    @Test
    fun `sin capacidad la confirmada con compromiso no se reenvia`() = runBlocking {
        sembrarConfirmada()
        val antes = fila()

        assertEquals(Result.success(), correr(Capacidades.CONTRATO_2))

        assertEquals(0, server.requestCount)
        assertEquals(antes, fila())
    }

    @Test
    fun `con el compromiso ya probado o resguardado no se reenvia`() = runBlocking {
        sembrarConfirmada()
        db.visitDao().confirmarCompromiso(id, confirmadaAntes)
        assertEquals(Result.success(), correr(Capacidades(3, true)))
        db.visitDao().insertVisit(
            fila().copy(
                COMPROMISO_CONFIRMADO_EN = null,
                COMPROMISO_RESGUARDADO_EN = confirmadaAntes
            )
        )
        assertEquals(Result.success(), correr(Capacidades(3, true)))
        assertEquals(0, server.requestCount)
    }

    private companion object {
        val CAMPOS =
            setOf(
                "promesa_venta_id",
                "promesa_fecha",
                "promesa_monto_centavos",
                "cita_fecha",
                "cita_hora"
            )
    }
}
