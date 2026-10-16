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
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
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
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * E-APP-065: el worker de visitas anota el motivo en CADA salida y, sobre todo,
 * **decide lo mismo** con el diagnóstico apagado (destino mudo), grabando, con
 * un destino que revienta y con la escritura en la base fallando.
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

    private data class Resultado(val result: ListenableWorker.Result, val fila: VisitEntity?, val peticiones: Int)

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

    private fun correr(e: Escenario, modo: Modo): Resultado {
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
                    diagnostico = sink(modo)
                )
            }).build()
            val result = runBlocking { (worker as PendingVisitsWorker).doWork() }
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
                        diagnostico = sink
                    )
                }).build()
            return runBlocking { (worker as PendingVisitsWorker).doWork() }
        }
        val sinId = Data.EMPTY
        val noExiste = Data.Builder().putString("visit_id", "visita-inexistente").build()
        listOf(sinId, noExiste).forEach { datos ->
            Modo.entries.forEach { modo ->
                assertEquals(
                    "$datos / $modo",
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

    private class ImagenesQueRevientan(private val db: AppDatabase) : VisitImageDao {
        override suspend fun insertAll(imagenes: List<VisitImageEntity>) =
            db.visitImageDao().insertAll(imagenes)
        override suspend fun getByVisitaId(visitaId: String) =
            db.visitImageDao().getByVisitaId(visitaId)
        override suspend fun getPendientesDe(visitaId: String): List<VisitImageEntity> =
            throw IllegalStateException("no se pudo leer visita_imagenes")
        override suspend fun marcarSubida(imagenId: String, subidaEn: String) =
            db.visitImageDao().marcarSubida(imagenId, subidaEn)
        override suspend fun rutasVivas(): List<String> = db.visitImageDao().rutasVivas()
        override suspend fun huerfanasAnterioresA(limite: String) =
            db.visitImageDao().huerfanasAnterioresA(limite)
        override suspend fun eliminar(imagenId: String) = db.visitImageDao().eliminar(imagenId)
    }
}
