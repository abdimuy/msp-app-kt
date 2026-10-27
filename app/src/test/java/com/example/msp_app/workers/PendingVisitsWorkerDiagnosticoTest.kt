package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.diagnosticoenvio.RegistradorDeEnvioDiagnostico
import com.example.msp_app.core.network.diagnostico.EnvioDiagnosticoSink
import com.example.msp_app.core.network.diagnostico.EnvioObservado
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.upload.HEADER_INTENT_CAPTURED
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.services.visits.CapacidadesDTO
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.api.services.visits.VisitaDetalleDTO
import com.example.msp_app.data.api.services.visits.VisitaExistenteDTO
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.Visit
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * E-APP-065: el worker de visitas anota el motivo en CADA salida y, sobre todo,
 * **decide lo mismo** con el diagnóstico apagado (destino mudo), grabando, con
 * un destino que revienta y con la escritura en la base fallando.
 *
 * Se prueba en los dos modos del kill-switch: el de reversa (`V2_ACTUAL`, las
 * pruebas de siempre) y el envío v3 (plan visitas P1, Tarea 11), que conserva
 * una anotación por corrida en cada `return`.
 *
 * El API es el `V2VisitsApi` REAL de Retrofit contra MockWebServer, salvo el
 * escenario de "excepción inesperada", que necesita un API que lance algo que
 * no es de red.
 */
class PendingVisitsWorkerDiagnosticoTest : RoomTestBase() {

    private enum class Modo { MUDO, GRABA, SINK_REVIENTA, DAO_REVIENTA }

    private data class Escenario(
        val nombre: String,
        val respuestas: List<MockResponse> = emptyList(),
        val intento: Int = 0,
        val apiFija: V2VisitsApi? = null,
        val imagenesRotas: Boolean = false,
        val codigo: String?,
        val http: Int?
    )

    /** [result] es el `Result` o, si `doWork` lanzó, el nombre de la excepción. */
    private data class Resultado(val result: Any, val fila: VisitEntity?, val peticiones: Int)

    private lateinit var context: Context
    private val clock = FakeClock(Instant.parse("2026-10-16T18:00:00Z"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val grabados = CopyOnWriteArrayList<EnvioObservado>()

    @Before
    fun setUpContext() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDownScope() {
        scope.cancel()
    }

    private fun visita() = VisitEntity(
        ID = "visita-001",
        CLIENTE_ID = 11486,
        COBRADOR = "Ramirez Ortiz, Fernando",
        COBRADOR_ID = 2991010,
        FECHA = "2026-10-16T09:30:00Z",
        FORMA_COBRO_ID = 0,
        LAT = 19.43,
        LNG = -99.13,
        NOTA = "No estaba, vuelvo el jueves",
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21552,
        IMPTE_DOCTO_CC_ID = 5000,
        GUARDADO_EN_MICROSIP = 0
    )

    private fun problema(code: Int, codigo: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"status":$code,"code":"$codigo","detail":"rechazada"}""")

    private fun apiQueRevienta(error: Exception) = object : V2VisitsApi {
        override suspend fun crearVisita(idempotencyKey: String, body: CrearVisitaBody): VisitaDTO =
            throw error

        override suspend fun crearVisitaConImagenes(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): VisitaDTO = throw error

        override suspend fun visitasExistentesPorIds(ids: String): List<String> =
            throw AssertionError(
                "no"
            )

        override suspend fun obtenerVisita(id: String): VisitaDetalleDTO =
            throw AssertionError("no se esperaba GET")

        override suspend fun capacidades(): CapacidadesDTO = throw AssertionError("no")

        override suspend fun visitasExistentesPorIdsDetalle(
            ids: String,
            detalle: Int
        ): List<VisitaExistenteDTO> = throw AssertionError("no")
    }

    private fun escenarios() = listOf(
        Escenario(
            "201",
            listOf(MockResponse().setResponseCode(201).setBody("""{"id":"visita-001"}""")),
            codigo = null,
            http = null
        ),
        Escenario(
            "409",
            listOf(problema(409, "visita_conflicto")),
            codigo = "visita_conflicto",
            http = 409
        ),
        Escenario(
            "422",
            listOf(problema(422, "visita_fecha_futura")),
            codigo = "visita_fecha_futura",
            http = 422
        ),
        Escenario("403", listOf(problema(403, "forbidden")), codigo = "forbidden", http = 403),
        Escenario(
            "500 intento 1",
            listOf(problema(500, "internal_error")),
            codigo = "internal_error",
            http = 500
        ),
        Escenario(
            "500 en el tope",
            listOf(problema(500, "internal_error")),
            intento = 9,
            codigo = "internal_error",
            http = 500
        ),
        Escenario(
            "401 en el tope",
            listOf(problema(401, "unauthorized")),
            intento = 9,
            codigo = "unauthorized",
            http = 401
        ),
        Escenario(
            "429",
            listOf(MockResponse().setResponseCode(429)),
            codigo = "http_429",
            http = 429
        ),
        Escenario(
            "IOException",
            listOf(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)),
            codigo = "network_error",
            http = 0
        ),
        Escenario(
            "timeout",
            listOf(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)),
            codigo = "timeout",
            http = 0
        ),
        Escenario(
            "excepcion inesperada",
            apiFija = apiQueRevienta(IllegalStateException("inesperado")),
            codigo = "error_inesperado",
            http = 0
        ),
        Escenario(
            "cancelacion",
            apiFija = apiQueRevienta(CancellationException("WorkManager detuvo")),
            codigo = "cancelado",
            http = 0
        ),
        Escenario(
            "comprobantes ilegibles",
            imagenesRotas = true,
            codigo = "comprobantes_ilegibles",
            http = 0
        )
    )

    private fun sink(modo: Modo): EnvioDiagnosticoSink = when (modo) {
        Modo.MUDO -> EnvioDiagnosticoSink { }
        Modo.GRABA -> EnvioDiagnosticoSink { grabados += it }
        Modo.SINK_REVIENTA -> EnvioDiagnosticoSink {
            throw IllegalStateException(
                "diagnóstico roto"
            )
        }
        Modo.DAO_REVIENTA -> RegistradorDeEnvioDiagnostico(
            dao = { throw IllegalStateException("base caída") },
            clock = clock,
            scope = scope
        )
    }

    private fun correr(
        e: Escenario,
        modo: Modo,
        envio: ModoEnvioVisitas = ModoEnvioVisitas.V2_ACTUAL
    ): Resultado {
        db.clearAllTables()
        val store = VisitsLocalDataSource(context)
        runBlocking { store.saveVisit(visita()) }
        val server = MockWebServer()
        e.respuestas.forEach { server.enqueue(it) }
        server.start()
        try {
            val api = e.apiFija ?: Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .client(OkHttpClient.Builder().readTimeout(400, TimeUnit.MILLISECONDS).build())
                .build()
                .create(V2VisitsApi::class.java)
            val imagenes = if (e.imagenesRotas) ImagenesQueRevientan(db) else db.visitImageDao()
            val worker = TestListenableWorkerBuilder<PendingVisitsWorker>(
                context,
                Data.Builder().putString("visit_id", "visita-001").build()
            ).setRunAttemptCount(e.intento).setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = PendingVisitsWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    visitsStore = VisitsLocalDataSource(appContext),
                    v2Api = api,
                    legacyApi = object : VisitsApi {
                        override suspend fun saveVisit(visit: Visit) =
                            throw AssertionError("legacy no")
                    },
                    useV2 = true,
                    maxAttempts = 10,
                    imagenes = imagenes,
                    clock = clock,
                    diagnostico = sink(modo),
                    modo = { envio }
                )
            }).build()
            val result: Any = try {
                runBlocking { (worker as PendingVisitsWorker).doWork() }
            } catch (c: CancellationException) {
                c::class.java.simpleName
            }
            val fila = runBlocking { store.getVisitById("visita-001") }
            return Resultado(result, fila, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `E-APP-065 la decision del worker de visitas es identica con y sin diagnostico`() {
        escenarios().forEach { e ->
            val base = correr(e, Modo.MUDO)
            Modo.entries.filter { it != Modo.MUDO }.forEach { modo ->
                assertEquals("${e.nombre} / $modo", base, correr(e, modo))
            }
        }
    }

    @Test
    fun `E-APP-065 cada salida anota su motivo, y el exito anota exito`() {
        escenarios().forEach { e ->
            grabados.clear()
            correr(e, Modo.GRABA)
            val unico = grabados.single()
            assertEquals(e.nombre, "visita", unico.tipo)
            assertEquals(e.nombre, "visita-001", unico.filaId)
            assertEquals(e.nombre, e.codigo, unico.fallo?.codigo)
            assertEquals(e.nombre, e.http, unico.fallo?.http)
        }
    }

    /** Control positivo: los escenarios recorren los caminos de decisión distintos. */
    @Test
    fun `controles positivos de los caminos`() {
        val r = escenarios().associate { it.nombre to correr(it, Modo.MUDO) }
        assertEquals(ListenableWorker.Result.success(), r.getValue("201").result)
        assertEquals(1, r.getValue("201").fila!!.GUARDADO_EN_MICROSIP)
        assertEquals(ListenableWorker.Result.success(), r.getValue("422").result)
        assertEquals(ListenableWorker.Result.retry(), r.getValue("500 intento 1").result)
        assertEquals(0, r.getValue("500 intento 1").fila!!.GUARDADO_EN_MICROSIP)
        assertEquals(ListenableWorker.Result.success(), r.getValue("500 en el tope").result)
        assertEquals(ListenableWorker.Result.failure(), r.getValue("401 en el tope").result)
        assertEquals(ListenableWorker.Result.retry(), r.getValue("IOException").result)
        assertEquals(ListenableWorker.Result.retry(), r.getValue("timeout").result)
        assertEquals(ListenableWorker.Result.retry(), r.getValue("excepcion inesperada").result)
        assertEquals(ListenableWorker.Result.retry(), r.getValue("cancelacion").result)
        assertEquals(ListenableWorker.Result.retry(), r.getValue("comprobantes ilegibles").result)
        assertEquals("ilegibles no manda nada", 0, r.getValue("comprobantes ilegibles").peticiones)
    }

    @Test
    fun `E-APP-065 las dos salidas por failure tambien anotan y siguen siendo failure`() {
        ModoEnvioVisitas.entries.forEach { envio -> salidasPorFailure(envio) }
    }

    private fun salidasPorFailure(envio: ModoEnvioVisitas) {
        fun correrSin(inputData: Data, sink: EnvioDiagnosticoSink): ListenableWorker.Result {
            val worker = TestListenableWorkerBuilder<PendingVisitsWorker>(context, inputData)
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ): ListenableWorker = PendingVisitsWorker(
                        appContext = appContext,
                        workerParams = workerParameters,
                        visitsStore = VisitsLocalDataSource(appContext),
                        v2Api = apiQueRevienta(IllegalStateException("no se debe llamar")),
                        legacyApi = object : VisitsApi {
                            override suspend fun saveVisit(visit: Visit) =
                                throw AssertionError("legacy no")
                        },
                        useV2 = true,
                        imagenes = db.visitImageDao(),
                        clock = clock,
                        diagnostico = sink,
                        modo = { envio }
                    )
                }).build()
            return runBlocking { (worker as PendingVisitsWorker).doWork() }
        }
        val sinId = Data.EMPTY
        val noExiste = Data.Builder().putString("visit_id", "visita-inexistente").build()
        listOf(sinId, noExiste).forEach { datos ->
            Modo.entries.forEach { modo ->
                assertEquals(
                    "$envio / $datos / $modo",
                    ListenableWorker.Result.failure(),
                    correrSin(datos, sink(modo))
                )
            }
        }
        grabados.clear()
        correrSin(sinId, sink(Modo.GRABA))
        correrSin(noExiste, sink(Modo.GRABA))
        assertEquals(
            listOf(
                "sin_visit_id" to "sin_visit_id",
                "visita-inexistente" to "visita_no_encontrada"
            ),
            grabados.map { it.filaId to it.fallo!!.codigo }
        )
    }

    // ─── modo v3 (plan visitas P1, Tarea 11) ─────────────────────────────────

    private fun conCustodia(r: MockResponse) = r.setHeader(
        HEADER_INTENT_CAPTURED,
        "c0ffee00-0000-4000-8000-000000000000"
    )

    private fun get404() = MockResponse().setResponseCode(404).setBody("""{"code":"not_found"}""")

    private fun escenariosV3() = listOf(
        Escenario(
            "201",
            listOf(MockResponse().setResponseCode(201).setBody("""{"id":"visita-001"}""")),
            codigo = null,
            http = null
        ),
        Escenario(
            "201 con eco ajeno",
            listOf(MockResponse().setResponseCode(201).setBody("""{"id":"otra"}"""), get404()),
            codigo = "visita_sin_eco",
            http = 200
        ),
        Escenario(
            "200 sin cuerpo",
            listOf(MockResponse().setResponseCode(200)),
            codigo = "network_error",
            http = 0
        ),
        Escenario(
            "409",
            listOf(problema(409, "visita_conflicto"), get404()),
            codigo = "visita_conflicto",
            http = 409
        ),
        Escenario(
            "422 sin cabecera",
            listOf(problema(422, "visita_fecha_futura"), get404()),
            codigo = "visita_fecha_futura",
            http = 422
        ),
        Escenario(
            "422 con cabecera",
            listOf(conCustodia(problema(422, "visita_fecha_futura")), get404()),
            codigo = "visita_fecha_futura",
            http = 422
        ),
        Escenario(
            "500 y GET completo",
            listOf(
                problema(500, "internal_error"),
                MockResponse().setResponseCode(
                    200
                ).setBody("""{"id":"visita-001","imagen_ids":[]}""")
            ),
            codigo = null,
            http = null
        ),
        Escenario(
            "500 y GET 404",
            listOf(problema(500, "internal_error"), get404()),
            codigo = "internal_error",
            http = 500
        ),
        Escenario(
            "500 en el intento 30",
            listOf(problema(500, "internal_error"), get404()),
            intento = 29,
            codigo = "internal_error",
            http = 500
        ),
        Escenario(
            "401 en el intento 30",
            listOf(problema(401, "unauthorized"), get404()),
            intento = 29,
            codigo = "unauthorized",
            http = 401
        ),
        Escenario(
            "IOException",
            listOf(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)),
            codigo = "network_error",
            http = 0
        ),
        Escenario(
            "timeout",
            listOf(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)),
            codigo = "timeout",
            http = 0
        ),
        Escenario(
            "excepcion inesperada",
            apiFija = apiQueRevienta(IllegalStateException("inesperado")),
            codigo = "error_inesperado",
            http = 0
        ),
        Escenario(
            "cancelacion",
            apiFija = apiQueRevienta(CancellationException("WorkManager detuvo")),
            codigo = "cancelado",
            http = 0
        ),
        Escenario(
            "comprobantes ilegibles",
            imagenesRotas = true,
            codigo = "comprobantes_ilegibles",
            http = 0
        )
    )

    @Test
    fun `E-APP-065 v3 la decision es identica con y sin diagnostico`() {
        escenariosV3().forEach { e ->
            val base = correr(e, Modo.MUDO, ModoEnvioVisitas.V3)
            Modo.entries.filter { it != Modo.MUDO }.forEach { modo ->
                assertEquals("${e.nombre} / $modo", base, correr(e, modo, ModoEnvioVisitas.V3))
            }
        }
    }

    @Test
    fun `E-APP-065 v3 cada salida anota una vez su motivo, y lo confirmado anota exito`() {
        escenariosV3().forEach { e ->
            grabados.clear()
            correr(e, Modo.GRABA, ModoEnvioVisitas.V3)
            val unico = grabados.single()
            assertEquals(e.nombre, "visita", unico.tipo)
            assertEquals(e.nombre, "visita-001", unico.filaId)
            assertEquals(e.nombre, e.codigo, unico.fallo?.codigo)
            assertEquals(e.nombre, e.http, unico.fallo?.http)
        }
    }

    /** Control positivo: en v3 los escenarios recorren las tres salidas y nada suelta sin prueba. */
    @Test
    fun `v3 controles positivos de los caminos`() {
        val r = escenariosV3().associate { it.nombre to correr(it, Modo.MUDO, ModoEnvioVisitas.V3) }
        val ok = ListenableWorker.Result.success()
        val retry = ListenableWorker.Result.retry()
        assertEquals(ok, r.getValue("201").result)
        assertEquals("201 sin GET", 1, r.getValue("201").peticiones)
        assertTrue(r.getValue("201").fila!!.CONFIRMADA_EN != null)
        assertEquals(ok, r.getValue("422 con cabecera").result)
        assertTrue(r.getValue("422 con cabecera").fila!!.RESGUARDADA_EN != null)
        assertEquals(ok, r.getValue("500 y GET completo").result)
        assertEquals(2, r.getValue("500 y GET completo").peticiones)
        assertTrue(r.getValue("500 y GET completo").fila!!.CONFIRMADA_EN != null)
        listOf(
            "201 con eco ajeno", "200 sin cuerpo", "409", "422 sin cabecera", "500 y GET 404",
            "500 en el intento 30", "401 en el intento 30", "IOException", "timeout",
            "excepcion inesperada", "comprobantes ilegibles"
        ).forEach { nombre ->
            assertEquals(nombre, retry, r.getValue(nombre).result)
            assertEquals(nombre, 0, r.getValue(nombre).fila!!.GUARDADO_EN_MICROSIP)
        }
        assertEquals("CancellationException", r.getValue("cancelacion").result)
        assertEquals(0, r.getValue("cancelacion").fila!!.GUARDADO_EN_MICROSIP)
        assertEquals("ilegibles no manda nada", 0, r.getValue("comprobantes ilegibles").peticiones)
    }

    private class ImagenesQueRevientan(private val db: AppDatabase) : VisitImageDao {
        override suspend fun insertAll(imagenes: List<VisitImageEntity>) =
            db.visitImageDao().insertAll(imagenes)
        override suspend fun getByVisitaId(visitaId: String) =
            db.visitImageDao().getByVisitaId(visitaId)
        override suspend fun getPendientesDe(visitaId: String): List<VisitImageEntity> =
            throw IllegalStateException("no se pudo leer visita_imagenes")
        override suspend fun marcarSubida(imagenId: String, subidaEn: String) =
            db.visitImageDao().marcarSubida(imagenId, subidaEn)
        override suspend fun pendientesDeEntregadas(): List<VisitImageEntity> =
            db.visitImageDao().pendientesDeEntregadas()

        override suspend fun rutasVivas(): List<String> = db.visitImageDao().rutasVivas()
        override suspend fun huerfanasAnterioresA(limite: String) =
            db.visitImageDao().huerfanasAnterioresA(limite)
        override suspend fun eliminar(imagenId: String) = db.visitImageDao().eliminar(imagenId)
        override fun observarSinSubir(): Flow<Int> = db.visitImageDao().observarSinSubir()
    }
}
