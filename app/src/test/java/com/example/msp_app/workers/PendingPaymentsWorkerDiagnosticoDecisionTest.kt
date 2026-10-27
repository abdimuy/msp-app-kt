package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.database.entities.EnvioDiagnosticoEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.diagnosticoenvio.RegistradorDeEnvioDiagnostico
import com.example.msp_app.core.network.diagnostico.EnvioDiagnosticoSink
import com.example.msp_app.core.network.diagnostico.EnvioPagoDiagnosticoInterceptor
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * E-APP-064 — **la prueba más importante del diagnóstico de pagos**: el
 * `PendingPaymentsWorker` REAL, con un `V2PaymentsApi` REAL de Retrofit contra
 * MockWebServer, decide exactamente lo mismo con el interceptor de diagnóstico
 * y sin él, en cada clase de falla.
 *
 * Por escenario y por modo se comparan: el `Result` del worker, la fila
 * `Payment` completa tras correr, la secuencia exacta de peticiones que llegó al
 * servidor (línea, cabeceras y cuerpo) y las llamadas a `encolarRefrescoDeSaldo`.
 *
 * Modos: SIN (cliente sin interceptor — el comportamiento de antes), CON
 * (interceptor + registrador real sobre Room), SINK_REVIENTA (el interceptor
 * revienta por dentro) y DAO_REVIENTA (falla la escritura en la base).
 */
class PendingPaymentsWorkerDiagnosticoDecisionTest : RoomTestBase() {

    private enum class Modo { SIN, CON, SINK_REVIENTA, DAO_REVIENTA }

    private data class Escenario(
        val nombre: String,
        val pago: PaymentEntity,
        val respuestas: List<MockResponse>,
        val codigoEsperado: String?
    )

    private data class Resultado(
        val result: ListenableWorker.Result,
        val fila: PaymentEntity?,
        val peticiones: List<String>,
        val refrescos: List<Pair<Int, Int>>
    )

    private lateinit var context: Context
    private val clock = FakeClock(Instant.parse("2026-10-16T18:00:00Z"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUpContext() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDownScope() {
        scope.cancel()
    }

    private fun pago(id: String = "pago-001", forma: Int = 87327) = PaymentEntity(
        ID = id,
        COBRADOR = "Mendoza Torres, Ana",
        DOCTO_CC_ACR_ID = 5000,
        DOCTO_CC_ID = -1,
        FECHA_HORA_PAGO = "2026-10-16T09:30:00.123Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 150.0,
        LAT = 19.4326,
        LNG = -99.1332,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 2991009,
        FORMA_COBRO_ID = forma,
        ZONA_CLIENTE_ID = 21552,
        NOMBRE_CLIENTE = "López García, Minerva"
    )

    private fun problema(code: Int, codigo: String, detalle: String, capturado: String? = null) =
        MockResponse().setResponseCode(code)
            .setHeader("Content-Type", "application/problem+json")
            .apply { if (capturado != null) setHeader("X-Intent-Captured", capturado) }
            .setBody("""{"status":$code,"code":"$codigo","detail":"$detalle"}""")

    private fun noExiste() = problema(404, "pago_not_found", "no existe")

    private fun escenarios(): List<Escenario> = listOf(
        Escenario(
            "2xx",
            pago(),
            listOf(
                MockResponse().setResponseCode(
                    200
                ).setBody("""{"id":"pago-001","docto_cc_id":8800123}""")
            ),
            null
        ),
        Escenario(
            "422 con X-Intent-Captured",
            pago(),
            listOf(problema(422, "validation_error", "el importe no cuadra", "fi-1"), noExiste()),
            "validation_error"
        ),
        Escenario(
            "422 sin X-Intent-Captured",
            pago(),
            listOf(problema(422, "validation_error", "el importe no cuadra"), noExiste()),
            "validation_error"
        ),
        Escenario(
            "422 condonacion pago_saldo_insuficiente capturada (el worker LEE el cuerpo)",
            pago(forma = Constants.CONDONACION_ID),
            listOf(
                problema(422, "pago_saldo_insuficiente", "el saldo no alcanza", "fi-2"),
                noExiste()
            ),
            "pago_saldo_insuficiente"
        ),
        Escenario(
            "500 problem+json",
            pago(),
            listOf(problema(500, "internal_error", "ocurrió un error interno"), noExiste()),
            "internal_error"
        ),
        Escenario(
            "502 HTML del tunel",
            pago(),
            listOf(
                MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"),
                noExiste()
            ),
            "http_502"
        ),
        Escenario(
            "error HTTP pero ya existe (RECONCILED_VIA_GET)",
            pago(),
            listOf(
                problema(500, "internal_error", "x"),
                MockResponse().setResponseCode(200).setBody("""{"id":"pago-001"}""")
            ),
            "internal_error"
        ),
        Escenario(
            "IOException (desconexión)",
            pago(),
            listOf(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)),
            "network_error"
        ),
        Escenario(
            "timeout",
            pago(),
            listOf(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)),
            "timeout"
        )
    )

    private fun correr(escenario: Escenario, modo: Modo): Resultado {
        db.clearAllTables()
        val store = PaymentsLocalDataSource(context)
        runBlocking { store.savePayment(escenario.pago) }

        val server = MockWebServer()
        escenario.respuestas.forEach { server.enqueue(it) }
        server.start()
        try {
            val refrescos = mutableListOf<Pair<Int, Int>>()
            val result = correrWorker(api(server, modo), refrescos)
            val peticiones = buildList {
                while (true) {
                    val r = server.takeRequest(100, TimeUnit.MILLISECONDS) ?: break
                    val boundary = r.getHeader(
                        "Content-Type"
                    )?.substringAfter("boundary=", "")?.takeIf {
                        it.isNotEmpty()
                    }
                    fun n(s: String) = if (boundary != null) s.replace(boundary, "B") else s
                    // `host` lleva el puerto aleatorio de cada MockWebServer: se quita.
                    val headers = r.headers.toMultimap() - "host"
                    add(n("${r.requestLine}|$headers|${r.body.readUtf8()}"))
                }
            }
            val fila = runBlocking { store.getPaymentById(escenario.pago.ID) }
            return Resultado(result, fila, peticiones, refrescos)
        } finally {
            server.shutdown()
        }
    }

    private fun api(server: MockWebServer, modo: Modo): V2PaymentsApi {
        val cliente = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(400, TimeUnit.MILLISECONDS)
        val sink: EnvioDiagnosticoSink? = when (modo) {
            Modo.SIN -> null
            Modo.CON -> RegistradorDeEnvioDiagnostico(dao = {
                db.envioDiagnosticoDao()
            }, clock = clock, scope = scope)
            Modo.SINK_REVIENTA -> EnvioDiagnosticoSink {
                throw IllegalStateException(
                    "interceptor roto"
                )
            }
            Modo.DAO_REVIENTA -> RegistradorDeEnvioDiagnostico(
                dao = { throw IllegalStateException("base caída") },
                clock = clock,
                scope = scope
            )
        }
        if (sink != null) cliente.addInterceptor(EnvioPagoDiagnosticoInterceptor(sink))
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .client(cliente.build())
            .build()
            .create(V2PaymentsApi::class.java)
    }

    private fun correrWorker(
        api: V2PaymentsApi,
        refrescos: MutableList<Pair<Int, Int>>
    ): ListenableWorker.Result {
        val worker = TestListenableWorkerBuilder<PendingPaymentsWorker>(
            context,
            Data.Builder().putString("payment_id", "pago-001").build()
        ).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = PendingPaymentsWorker(
                appContext = appContext,
                workerParams = workerParameters,
                paymentsStore = PaymentsLocalDataSource(appContext),
                v2Api = api,
                legacyApi = object : PaymentsApi {
                    override suspend fun savePayment(request: PaymentRequest) =
                        throw AssertionError("legacy no")
                },
                useV2 = true,
                imagenes = db.paymentImageDao(),
                clock = clock,
                encolarRefrescoDeSaldo = { zona, cargo -> refrescos += zona to cargo }
            )
        }).build()
        return runBlocking { (worker as PendingPaymentsWorker).doWork() }
    }

    @Test
    fun `E-APP-064 la decision del worker de pagos es identica con y sin diagnostico en cada clase de falla`() {
        escenarios().forEach { escenario ->
            val base = correr(escenario, Modo.SIN)
            Modo.entries.filter { it != Modo.SIN }.forEach { modo ->
                assertEquals("${escenario.nombre} / $modo", base, correr(escenario, modo))
            }
        }
    }

    /**
     * Controles positivos: los escenarios SÍ ejercen los caminos que dicen —si
     * no, "idéntico" no probaría nada— y el modo CON SÍ anota el motivo.
     */
    @Test
    fun `controles positivos - cada escenario toma su camino y el diagnostico se anota`() {
        val porNombre = escenarios().associateBy { it.nombre }

        val ok = correr(porNombre.getValue("2xx"), Modo.SIN)
        assertEquals(ListenableWorker.Result.success(), ok.result)
        assertEquals(true, ok.fila!!.GUARDADO_EN_MICROSIP)
        assertEquals(8800123, ok.fila.DOCTO_CC_ID)

        // E-APP-088: la custodia sin prueba de existencia (el GET da 404) ya no
        // suelta la captura: queda pendiente, con aviso y sin reenvío.
        val capturado = correr(porNombre.getValue("422 con X-Intent-Captured"), Modo.SIN)
        assertEquals(ListenableWorker.Result.success(), capturado.result)
        assertEquals(false, capturado.fila!!.GUARDADO_EN_MICROSIP)

        val sinCustodia = correr(porNombre.getValue("422 sin X-Intent-Captured"), Modo.SIN)
        assertEquals(ListenableWorker.Result.retry(), sinCustodia.result)
        assertEquals(false, sinCustodia.fila!!.GUARDADO_EN_MICROSIP)

        val condonacion =
            correr(
                porNombre.getValue(
                    "422 condonacion pago_saldo_insuficiente capturada (el worker LEE el cuerpo)"
                ),
                Modo.SIN
            )
        assertEquals(
            "el worker leyó el code del cuerpo y soltó la condonación",
            1,
            condonacion.refrescos.size
        )

        assertEquals(
            ListenableWorker.Result.retry(),
            correr(porNombre.getValue("IOException (desconexión)"), Modo.SIN).result
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            correr(porNombre.getValue("timeout"), Modo.SIN).result
        )

        escenarios().filter { it.codigoEsperado != null }.forEach { escenario ->
            correr(escenario, Modo.CON)
            val fila = esperarFila(escenario.pago.ID, escenario.codigoEsperado!!)
            assertNotNull("${escenario.nombre}: el modo CON debió anotar", fila)
            assertEquals(escenario.nombre, escenario.codigoEsperado, fila!!.ULTIMO_ERROR_CODIGO)
        }
    }

    private fun esperarFila(id: String, codigo: String): EnvioDiagnosticoEntity? {
        repeat(200) {
            val fila = runBlocking { db.envioDiagnosticoDao().obtener("pago", id) }
            if (fila?.ULTIMO_ERROR_CODIGO == codigo) return fila
            Thread.sleep(10)
        }
        return null
    }
}
