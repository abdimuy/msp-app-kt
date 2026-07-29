package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.data.api.services.payment.PagoRecibidoDTO
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
 * GUARDADO_EN_MICROSIP=true ONLY when the server is known to hold it
 * (2xx, or a captured 4xx), never on a network failure.
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

    private fun fakeV2Api(
        crear: suspend (idempotencyKey: String, datos: RequestBody) -> PagoRecibidoDTO
    ): V2PaymentsApi = object : V2PaymentsApi {
        override suspend fun crearPago(
            idempotencyKey: String,
            datos: RequestBody
        ): PagoRecibidoDTO = crear(idempotencyKey, datos)
    }

    private fun happyApi(): V2PaymentsApi = fakeV2Api { _, _ -> PagoRecibidoDTO(id = "pago-001") }

    private fun throwingLegacyApi(): PaymentsApi = object : PaymentsApi {
        override suspend fun savePayment(request: PaymentRequest) {
            throw AssertionError("legacy savePayment must not be called in v2 tests")
        }
    }

    private fun httpError(code: Int, body: String = "{}"): HttpException = HttpException(
        Response.error<PagoRecibidoDTO>(
            code,
            body.toResponseBody("application/json".toMediaTypeOrNull())
        )
    )

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
        maxAttempts: Int = 10,
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
                    useV2 = useV2,
                    maxAttempts = maxAttempts
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
    fun v2_422_validation_marks_done_because_captured_server_side() = runTest {
        seed(pendingPayment())

        val api = fakeV2Api { _, _ ->
            throw httpError(
                422,
                """{"code":"pago_cargo_no_encontrado","detail":"el cargo no existe"}"""
            )
        }

        val result = buildAndRunWorker(api = api)

        assertEquals(
            "A 422 is captured as a failed-intent server-side; the phone is done",
            ListenableWorker.Result.success(),
            result
        )
        assertTrue(
            "GUARDADO must flip to true — resolution lives desk-side",
            guardadoFlag("pago-001")
        )
    }

    @Test
    fun v2_403_marks_done() = runTest {
        seed(pendingPayment())
        val api = fakeV2Api { _, _ -> throw httpError(403) }

        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertTrue(guardadoFlag("pago-001"))
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

    @Test
    fun v2_5xx_below_cap_returns_retry() = runTest {
        seed(pendingPayment())
        val api = fakeV2Api { _, _ -> throw httpError(500) }

        val result = buildAndRunWorker(api = api, maxAttempts = 3, runAttemptCount = 0)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertFalse("5xx below the cap must keep retrying, not mark done", guardadoFlag("pago-001"))
    }

    @Test
    fun v2_5xx_at_cap_marks_done() = runTest {
        seed(pendingPayment())
        val api = fakeV2Api { _, _ -> throw httpError(503) }

        // maxAttempts=3, runAttemptCount=2 → this is the 3rd (final) attempt.
        val result = buildAndRunWorker(api = api, maxAttempts = 3, runAttemptCount = 2)

        assertEquals(
            "at the attempt cap a server 5xx (captured server-side) is marked done",
            ListenableWorker.Result.success(),
            result
        )
        assertTrue(guardadoFlag("pago-001"))
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
