package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.payment.PagoRecibidoDTO
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Unit tests for the v2 upload path of [PendingPaymentsWorker].
 *
 * Room is backed by the in-memory DB from [RoomTestBase]; the network is a fake
 * [V2PaymentsApi]. The worker is built directly (no WorkManager, no Firebase —
 * the worker logs via android.util.Log only).
 *
 * The invariant under test is the robustness rule: the pago is marked
 * GUARDADO_EN_MICROSIP=true ONLY when the server is known to hold it — 2xx,
 * a captured 4xx, or a 5xx that msp-api itself produced (confirmed by the
 * `application/problem+json` Content-Type; see [httpError]). A network
 * failure or a gateway/proxy 5xx (no problem+json) never marks done.
 */
class PendingPaymentsWorkerV2Test : RoomTestBase() {

    private lateinit var context: Context
    private lateinit var paymentsStore: PaymentsLocalDataSource

    @Before
    fun setUpWorker() {
        context = ApplicationProvider.getApplicationContext()
        paymentsStore = PaymentsLocalDataSource(context)
    }

    // ─── fixtures ──────────────────────────────────────────────────────────────

    /**
     * A pending pago. LAT=0.0 is the "no fix" sentinel (must be omitted);
     * LNG carries a real coordinate (must serialize as `lon`). FECHA has a
     * fractional-second component that must be truncated for Go's time.RFC3339.
     */
    private fun pendingPayment(id: String = "pago-001", guardado: Boolean = false) = PaymentEntity(
        ID = id,
        COBRADOR = "Mendoza Torres, Ana",
        DOCTO_CC_ACR_ID = 5000,
        DOCTO_CC_ID = 6000,
        FECHA_HORA_PAGO = "2026-06-01T09:30:00.123Z",
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = 1500.0,
        LAT = 0.0,
        LNG = 19.43,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 200,
        FORMA_COBRO_ID = 87327,
        ZONA_CLIENTE_ID = 21552,
        NOMBRE_CLIENTE = "López García, Minerva"
    )

    private suspend fun seed(payment: PaymentEntity) = paymentsStore.savePayment(payment)

    private suspend fun guardadoFlag(id: String): Boolean =
        paymentsStore.getPaymentById(id)!!.GUARDADO_EN_MICROSIP

    // ─── fakes ─────────────────────────────────────────────────────────────────

    // `crear` va AL FINAL para que la lambda de cola siga ligándose a ella.
    private fun fakeV2Api(
        obtener: suspend (id: String) -> PagoRecibidoDTO = { throw httpError(404) },
        crear: suspend (idempotencyKey: String, datos: RequestBody) -> PagoRecibidoDTO
    ): V2PaymentsApi = object : V2PaymentsApi {
        override suspend fun crearPago(
            idempotencyKey: String,
            datos: RequestBody
        ): PagoRecibidoDTO = crear(idempotencyKey, datos)

        override suspend fun obtenerPago(id: String): PagoRecibidoDTO = obtener(id)
    }

    private fun happyApi(): V2PaymentsApi = fakeV2Api { _, _ -> PagoRecibidoDTO(id = "pago-001") }

    private fun throwingLegacyApi(): PaymentsApi = object : PaymentsApi {
        override suspend fun savePayment(request: PaymentRequest) {
            throw AssertionError("legacy savePayment must not be called in v2 tests")
        }
    }

    /**
     * Builds an [HttpException] with a real `Content-Type` header on the raw
     * HTTP response — `retrofit2.Response.error(code, body)` does NOT copy the
     * body's media type into the response headers, so testing the
     * `problem+json` signal requires the 2-arg overload with a hand-built raw
     * [okhttp3.Response]. Pass `contentType = null` to simulate a response
     * with the header entirely absent (some gateways omit it).
     */
    private fun httpError(
        code: Int,
        body: String = "{}",
        contentType: String? = "application/problem+json",
        intentCaptured: String? = null
    ): HttpException {
        val responseBody = body.toResponseBody(contentType?.toMediaTypeOrNull())
        val rawResponseBuilder = okhttp3.Response.Builder()
            .code(code)
            .message("test")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
        if (contentType != null) {
            rawResponseBuilder.header("Content-Type", contentType)
        }
        // Única prueba de custodia: el servidor la emite sólo cuando su
        // Store.Save tuvo éxito.
        if (intentCaptured != null) {
            rawResponseBuilder.header("X-Intent-Captured", intentCaptured)
        }
        return HttpException(
            Response.error<PagoRecibidoDTO>(responseBody, rawResponseBuilder.build())
        )
    }

    private fun RequestBody.asString(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }

    // ─── worker runner ───────────────────────────────────────────────────────────

    private fun buildAndRunWorker(
        paymentId: String? = "pago-001",
        api: V2PaymentsApi = happyApi(),
        legacyApi: PaymentsApi = throwingLegacyApi(),
        useV2: Boolean = true,
        runAttemptCount: Int = 0
    ): ListenableWorker.Result {
        val inputBuilder = Data.Builder()
        if (paymentId != null) inputBuilder.putString("payment_id", paymentId)

        val worker = TestListenableWorkerBuilder<PendingPaymentsWorker>(
            context,
            inputBuilder.build()
        )
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = PendingPaymentsWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    paymentsStore = PaymentsLocalDataSource(appContext),
                    v2Api = api,
                    legacyApi = legacyApi,
                    useV2 = useV2
                )
            })
            .build()

        var result: ListenableWorker.Result = ListenableWorker.Result.failure()
        runBlocking { result = (worker as PendingPaymentsWorker).doWork() }
        return result
    }

    // ─── tests ────────────────────────────────────────────────────────────────

    @Test
    fun v2_happy_path_marks_guardado() = runTest {
        seed(pendingPayment())

        var callCount = 0
        var capturedKey: String? = null
        val api = fakeV2Api { key, _ ->
            callCount++
            capturedKey = key
            PagoRecibidoDTO(id = "pago-001")
        }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("GUARDADO_EN_MICROSIP must flip to true after 2xx", guardadoFlag("pago-001"))
        assertEquals("crearPago must be called exactly once", 1, callCount)
        assertEquals("Idempotency-Key must equal the pago ID", "pago-001", capturedKey)
    }

    @Test
    fun v2_duplicate_200_is_idempotent_and_marks_done() = runTest {
        seed(pendingPayment())

        var callCount = 0
        val api = fakeV2Api { _, _ ->
            callCount++
            PagoRecibidoDTO(id = "pago-001")
        }

        // Two independent worker runs (e.g. a forced retry). The server dedupes
        // by datos.id; both must succeed with no double-collection signalled.
        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))

        assertTrue(guardadoFlag("pago-001"))
        assertEquals("worker resends on each run; server is the dedupe authority", 2, callCount)
    }

    @Test
    fun v2_422_con_custodia_confirmada_suelta_la_captura() = runTest {
        seed(pendingPayment())

        val api = fakeV2Api { _, _ ->
            throw httpError(
                422,
                """{"code":"pago_cargo_no_encontrado","detail":"el cargo no existe"}""",
                intentCaptured = "3f2a1c7e-0000-4000-8000-000000000001"
            )
        }

        val result = buildAndRunWorker(api = api)

        assertEquals(
            "un 422 resguardado (X-Intent-Captured) lo corrige la oficina",
            ListenableWorker.Result.success(),
            result
        )
        assertTrue(
            "GUARDADO debe pasar a true: el servidor lo tiene resguardado",
            guardadoFlag("pago-001")
        )
    }

    @Test
    fun v2_422_sin_custodia_confirmada_se_reintenta() = runTest {
        // El caso del pool trabado: la petición falla Y la captura falla. La
        // respuesta sigue siendo problem+json, pero nadie tiene el pago.
        seed(pendingPayment())

        val api = fakeV2Api { _, _ ->
            throw httpError(422, intentCaptured = null)
        }

        assertEquals(ListenableWorker.Result.retry(), buildAndRunWorker(api = api))
        assertFalse(
            "sin custodia confirmada el pago NO puede soltarse",
            guardadoFlag("pago-001")
        )
    }

    @Test
    fun v2_403_con_custodia_suelta_y_sin_custodia_reintenta() = runTest {
        seed(pendingPayment())
        val conCustodia = fakeV2Api { _, _ -> throw httpError(403, intentCaptured = "abc") }
        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = conCustodia))
        assertTrue(guardadoFlag("pago-001"))

        seed(pendingPayment(id = "pago-002"))
        val sinCustodia = fakeV2Api { _, _ -> throw httpError(403) }
        assertEquals(
            ListenableWorker.Result.retry(),
            buildAndRunWorker(paymentId = "pago-002", api = sinCustodia)
        )
        assertFalse(guardadoFlag("pago-002"))
    }

    @Test
    fun v2_404_del_tunel_se_reintenta_y_nunca_suelta() = runTest {
        // Antes un 404 se leía como rechazo definitivo del API y soltaba el
        // pago. Un 404 puede venir de un túnel/proxy que nunca lo entregó.
        seed(pendingPayment())
        val api = fakeV2Api { _, _ -> throw httpError(404, contentType = null) }

        assertEquals(ListenableWorker.Result.retry(), buildAndRunWorker(api = api))
        assertFalse(guardadoFlag("pago-001"))
    }

    @Test
    fun v2_get_200_tras_error_reconcilia_sin_reintentar() = runTest {
        // Prueba de EXISTENCIA: el servidor ya lo tiene (una corrida anterior
        // cuyo 2xx no llegó, o un replay desde la oficina).
        seed(pendingPayment())
        var getCalls = 0
        val api = fakeV2Api(
            crear = { _, _ -> throw httpError(500, intentCaptured = null) },
            obtener = { id ->
                getCalls++
                PagoRecibidoDTO(id = id, sincronizacion = "aplicada")
            }
        )

        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertTrue(
            "el GET lo encontró: la captura se suelta aunque no hubiera custodia",
            guardadoFlag("pago-001")
        )
        assertEquals("el GET se consulta una vez por intento", 1, getCalls)
    }

    @Test
    fun v2_get_indeterminado_no_se_lee_como_inexistente() = runTest {
        // Un GET que falla con 500 NO es "no existe": se sigue a la tabla, y
        // sin custodia confirmada eso es reintentar.
        seed(pendingPayment())
        val api = fakeV2Api(
            crear = { _, _ -> throw httpError(500) },
            obtener = { throw httpError(500) }
        )

        assertEquals(ListenableWorker.Result.retry(), buildAndRunWorker(api = api))
        assertFalse(guardadoFlag("pago-001"))
    }

    @Test
    fun v2_persiste_docto_cc_id_del_servidor() = runTest {
        seed(pendingPayment())
        val api = fakeV2Api { _, _ ->
            PagoRecibidoDTO(id = "pago-001", docto_cc_id = 987654)
        }

        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertEquals(
            "sin DOCTO_CC_ID el pago local y el del servidor no se reconocen y los totales se inflan",
            987654,
            paymentsStore.getPaymentById("pago-001")!!.DOCTO_CC_ID
        )
    }

    @Test
    fun v2_401_returns_retry_and_keeps_pending() = runTest {
        seed(pendingPayment())
        val api = fakeV2Api { _, _ -> throw httpError(401) }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertFalse("a 401 blip must not mark the pago done", guardadoFlag("pago-001"))
    }

    @Test
    fun v2_409_returns_retry() = runTest {
        seed(pendingPayment())
        val api = fakeV2Api { _, _ -> throw httpError(409) }

        assertEquals(ListenableWorker.Result.retry(), buildAndRunWorker(api = api))
        assertFalse(guardadoFlag("pago-001"))
    }

    /**
     * A 5xx that msp-api ITSELF produced answers with the uniform
     * `application/problem+json` error envelope (see msp-api `response.go`).
     * That means the failed-intent capture middleware already ran and
     * persisted it once — retrying forever would only spam that inbox with
     * duplicate captures (fresh ID per attempt, no dedup). So it is DONE:
     * the desk resolves it from there.
     */
    @Test
    fun v2_5xx_from_msp_api_sin_custodia_se_reintenta() = runTest {
        seed(pendingPayment())
        val api =
            fakeV2Api { _, _ -> throw httpError(500, contentType = "application/problem+json") }

        val result = buildAndRunWorker(api = api)

        assertEquals(
            "problem+json prueba que llegó al API, NO que se haya resguardado: " +
                "cuando el pool se traba fallan la petición y la captura a la vez",
            ListenableWorker.Result.retry(),
            result
        )
        assertFalse(
            "este es exactamente el caso que perdió dos pagos el 2026-08-13",
            guardadoFlag("pago-001")
        )
    }

    /**
     * Garantía de durabilidad (decisión de producto): un 5xx de gateway/proxy
     * en frente de msp-api NUNCA llegó a la captura de fallidos — no trae
     * `problem+json`. Asumir "ya capturado" podía perder el pago, así que
     * reintenta por siempre y JAMÁS marca listo, sin importar el
     * `Content-Type` exacto (HTML, texto plano, o el header ausente).
     */
    @Test
    fun v2_5xx_from_gateway_returns_retry_and_never_marks_done() = runTest {
        listOf("text/html", null).forEachIndexed { index, contentType ->
            val id = "pago-gw-$index"
            seed(pendingPayment(id = id))
            val api = fakeV2Api { _, _ -> throw httpError(502, contentType = contentType) }

            val result = buildAndRunWorker(paymentId = id, api = api)

            assertEquals(
                "contentType=$contentType: a gateway 5xx (no problem+json) must retry",
                ListenableWorker.Result.retry(),
                result
            )
            assertFalse(
                "contentType=$contentType: must never mark done — never reached msp-api",
                guardadoFlag(id)
            )
        }
    }

    /**
     * Extiende la garantía anterior: un 5xx de gateway repetido, sin importar
     * cuántos intentos lleve, JAMÁS marca el pago como guardado. No hay tope
     * de intentos: mejor atorado-y-visible que perdido.
     */
    @Test
    fun v2_5xx_from_gateway_repeated_indefinitely_never_marks_done() = runTest {
        seed(pendingPayment())
        val api = fakeV2Api { _, _ -> throw httpError(503, contentType = "text/html") }

        // Simula intentos muy avanzados (lo que antes era "el tope").
        val result = buildAndRunWorker(api = api, runAttemptCount = 999)

        assertEquals(
            "a gateway 5xx never reaches DONE, no matter how many attempts",
            ListenableWorker.Result.retry(),
            result
        )
        assertFalse(
            "a repeated gateway 5xx must NEVER mark the pago done — durability guarantee",
            guardadoFlag("pago-001")
        )
    }

    @Test
    fun v2_network_error_returns_retry_and_never_marks_done() = runTest {
        seed(pendingPayment())
        val api = fakeV2Api { _, _ -> throw IOException("connection reset") }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertFalse("a network failure must NEVER mark the pago done", guardadoFlag("pago-001"))
    }

    @Test
    fun v2_body_formats_importe_fecha_and_lon() = runTest {
        seed(pendingPayment())

        var capturedJson: String? = null
        val api = fakeV2Api { _, datos ->
            capturedJson = datos.asString()
            PagoRecibidoDTO(id = "pago-001")
        }

        buildAndRunWorker(api = api)

        val json = JsonParser.parseString(capturedJson).asJsonObject
        assertEquals(
            "importe must be a 2-decimal fixed string",
            "1500.00",
            json["importe"].asString
        )
        assertEquals(
            "fecha_hora_pago must be RFC3339 with no fractional seconds",
            "2026-06-01T09:30:00Z",
            json["fecha_hora_pago"].asString
        )
        assertEquals(
            "cargo_docto_cc_id maps from DOCTO_CC_ACR_ID",
            5000,
            json["cargo_docto_cc_id"].asInt
        )
        assertEquals("lon maps from LNG", "19.43", json["lon"].asString)
        assertFalse("lat must be omitted when it is the 0.0 sentinel", json.has("lat"))
    }

    @Test
    fun missing_payment_id_returns_failure() = runTest {
        val result = buildAndRunWorker(paymentId = null)
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun payment_not_found_returns_failure() = runTest {
        // Nothing seeded.
        val result = buildAndRunWorker(paymentId = "does-not-exist")
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun legacy_path_used_when_useV2_false() = runTest {
        seed(pendingPayment())

        var legacyCalled = false
        val legacy = object : PaymentsApi {
            override suspend fun savePayment(request: PaymentRequest) {
                legacyCalled = true
            }
        }
        // v2 api throws so the test fails loudly if the legacy gate is wrong.
        val v2 =
            fakeV2Api { _, _ -> throw AssertionError("v2 must not be called when useV2=false") }

        val result = buildAndRunWorker(api = v2, legacyApi = legacy, useV2 = false)

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("legacy savePayment must be called when useV2=false", legacyCalled)
        assertTrue(guardadoFlag("pago-001"))
    }

    @Test
    fun v2_success_response_id_is_optional() = runTest {
        seed(pendingPayment())
        // Server returns an empty body → Gson leaves id null; worker must not care.
        val api = fakeV2Api { _, _ -> PagoRecibidoDTO() }

        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertTrue(guardadoFlag("pago-001"))
    }
}
