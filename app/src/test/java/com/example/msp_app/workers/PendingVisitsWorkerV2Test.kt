package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.Visit
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Unit tests for the v2 upload path of [PendingVisitsWorker].
 *
 * Room is backed by the in-memory DB from [RoomTestBase]; the network is a
 * fake [V2VisitsApi]. The worker is built directly (no WorkManager, no
 * Firebase — the worker logs via android.util.Log only). Mirrors
 * [PendingPaymentsWorkerV2Test].
 *
 * The invariant under test is the robustness rule: the visita is marked
 * GUARDADO_EN_MICROSIP=1 ONLY when the server is known to hold it (2xx, or a
 * captured 4xx), never on a network failure.
 */
class PendingVisitsWorkerV2Test : RoomTestBase() {

    private lateinit var context: Context
    private lateinit var visitsStore: VisitsLocalDataSource

    @Before
    fun setUpWorker() {
        context = ApplicationProvider.getApplicationContext()
        visitsStore = VisitsLocalDataSource(context)
    }

    // ─── fixtures ──────────────────────────────────────────────────────────────

    private fun pendingVisit(id: String = "visita-001", guardado: Int = 0) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Ramirez Ortiz, Fernando",
        COBRADOR_ID = 200,
        FECHA = "2026-06-01T09:30:00Z",
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        NOTA = "El cliente pidio pasar la proxima semana",
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21552,
        IMPTE_DOCTO_CC_ID = 5000,
        GUARDADO_EN_MICROSIP = guardado
    )

    private suspend fun seed(visit: VisitEntity) = visitsStore.saveVisit(visit)

    private suspend fun guardadoFlag(id: String): Int =
        visitsStore.getVisitById(id).GUARDADO_EN_MICROSIP

    // ─── fakes ─────────────────────────────────────────────────────────────────

    private fun fakeV2Api(
        crear: suspend (idempotencyKey: String, body: CrearVisitaBody) -> VisitaDTO
    ): V2VisitsApi = object : V2VisitsApi {
        override suspend fun crearVisita(idempotencyKey: String, body: CrearVisitaBody): VisitaDTO =
            crear(idempotencyKey, body)
    }

    private fun happyApi(): V2VisitsApi = fakeV2Api { _, _ -> VisitaDTO(id = "visita-001") }

    private fun throwingLegacyApi(): VisitsApi = object : VisitsApi {
        override suspend fun saveVisit(visit: Visit) {
            throw AssertionError("legacy saveVisit must not be called in v2 tests")
        }
    }

    private fun httpError(code: Int, body: String = "{}"): HttpException = HttpException(
        Response.error<VisitaDTO>(
            code,
            body.toResponseBody("application/json".toMediaTypeOrNull())
        )
    )

    // ─── worker runner ───────────────────────────────────────────────────────────

    private fun buildAndRunWorker(
        visitId: String? = "visita-001",
        api: V2VisitsApi = happyApi(),
        legacyApi: VisitsApi = throwingLegacyApi(),
        useV2: Boolean = true,
        maxAttempts: Int = 10,
        runAttemptCount: Int = 0
    ): ListenableWorker.Result {
        val inputBuilder = Data.Builder()
        if (visitId != null) inputBuilder.putString("visit_id", visitId)

        val worker = TestListenableWorkerBuilder<PendingVisitsWorker>(
            context,
            inputBuilder.build()
        )
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = PendingVisitsWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    visitsStore = VisitsLocalDataSource(appContext),
                    v2Api = api,
                    legacyApi = legacyApi,
                    useV2 = useV2,
                    maxAttempts = maxAttempts
                )
            })
            .build()

        var result: ListenableWorker.Result = ListenableWorker.Result.failure()
        runBlocking { result = (worker as PendingVisitsWorker).doWork() }
        return result
    }

    // ─── tests ────────────────────────────────────────────────────────────────

    @Test
    fun v2_happy_path_marks_guardado() = runTest {
        seed(pendingVisit())

        var callCount = 0
        var capturedKey: String? = null
        val api = fakeV2Api { key, _ ->
            callCount++
            capturedKey = key
            VisitaDTO(id = "visita-001")
        }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(
            "GUARDADO_EN_MICROSIP must flip to 1 after 2xx",
            1,
            guardadoFlag("visita-001")
        )
        assertEquals("crearVisita must be called exactly once", 1, callCount)
        assertEquals("Idempotency-Key must equal the visita ID", "visita-001", capturedKey)
    }

    @Test
    fun v2_duplicate_200_is_idempotent_and_marks_done() = runTest {
        seed(pendingVisit())

        var callCount = 0
        val api = fakeV2Api { _, _ ->
            callCount++
            VisitaDTO(id = "visita-001")
        }

        // Two independent worker runs (e.g. a forced retry). The server dedupes
        // by body.id; both must succeed with no double-adverse effect.
        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))

        assertEquals(1, guardadoFlag("visita-001"))
        assertEquals("worker resends on each run; server is the dedupe authority", 2, callCount)
    }

    @Test
    fun v2_422_validation_marks_done_because_captured_server_side() = runTest {
        seed(pendingVisit())

        val api = fakeV2Api { _, _ ->
            throw httpError(
                422,
                """{"code":"visita_cliente_no_encontrado","detail":"el cliente no existe"}"""
            )
        }

        val result = buildAndRunWorker(api = api)

        assertEquals(
            "A 422 is captured as a failed-intent server-side; the phone is done",
            ListenableWorker.Result.success(),
            result
        )
        assertEquals(
            "GUARDADO must flip to 1 — resolution lives desk-side",
            1,
            guardadoFlag("visita-001")
        )
    }

    @Test
    fun v2_403_marks_done() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw httpError(403) }

        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertEquals(1, guardadoFlag("visita-001"))
    }

    @Test
    fun v2_401_returns_retry_and_keeps_pending() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw httpError(401) }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals("a 401 blip must not mark the visita done", 0, guardadoFlag("visita-001"))
    }

    @Test
    fun v2_409_returns_retry() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw httpError(409) }

        assertEquals(ListenableWorker.Result.retry(), buildAndRunWorker(api = api))
        assertEquals(0, guardadoFlag("visita-001"))
    }

    @Test
    fun v2_5xx_below_cap_returns_retry() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw httpError(500) }

        val result = buildAndRunWorker(api = api, maxAttempts = 3, runAttemptCount = 0)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(
            "5xx below the cap must keep retrying, not mark done",
            0,
            guardadoFlag("visita-001")
        )
    }

    @Test
    fun v2_5xx_at_cap_marks_done() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw httpError(503) }

        // maxAttempts=3, runAttemptCount=2 → this is the 3rd (final) attempt.
        val result = buildAndRunWorker(api = api, maxAttempts = 3, runAttemptCount = 2)

        assertEquals(
            "at the attempt cap a server 5xx (captured server-side) is marked done",
            ListenableWorker.Result.success(),
            result
        )
        assertEquals(1, guardadoFlag("visita-001"))
    }

    @Test
    fun v2_network_error_returns_retry_and_never_marks_done() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw IOException("connection reset") }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(
            "a network failure must NEVER mark the visita done",
            0,
            guardadoFlag("visita-001")
        )
    }

    @Test
    fun missing_visit_id_returns_failure() = runTest {
        val result = buildAndRunWorker(visitId = null)
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun visit_not_found_returns_failure() = runTest {
        // Nothing seeded.
        val result = buildAndRunWorker(visitId = "does-not-exist")
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun legacy_path_used_when_useV2_false() = runTest {
        seed(pendingVisit())

        var legacyCalled = false
        val legacy = object : VisitsApi {
            override suspend fun saveVisit(visit: Visit) {
                legacyCalled = true
            }
        }
        // v2 api throws so the test fails loudly if the legacy gate is wrong.
        val v2 =
            fakeV2Api { _, _ -> throw AssertionError("v2 must not be called when useV2=false") }

        val result = buildAndRunWorker(api = v2, legacyApi = legacy, useV2 = false)

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("legacy saveVisit must be called when useV2=false", legacyCalled)
        assertEquals(1, guardadoFlag("visita-001"))
    }

    @Test
    fun v2_success_response_id_is_optional() = runTest {
        seed(pendingVisit())
        // Server returns an empty body → Gson leaves id null; worker must not care.
        val api = fakeV2Api { _, _ -> VisitaDTO() }

        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertEquals(1, guardadoFlag("visita-001"))
    }
}
