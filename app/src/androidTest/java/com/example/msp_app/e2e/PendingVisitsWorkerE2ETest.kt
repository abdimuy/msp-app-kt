package com.example.msp_app.e2e

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.local.entities.VisitEntity
import com.example.msp_app.workers.PendingVisitsWorker
import com.example.msp_app.workmanager.enqueuePendingVisitsWorker
import com.google.gson.GsonBuilder
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * B6/B7 — on-device e2e of [PendingVisitsWorker] driven by the REAL
 * WorkManager scheduler (test mode), talking to a [MockWebServer] standing
 * in for msp-api's `POST /v2/visitas`. Mirrors
 * [PendingPaymentsWorkerE2ETest] closely, but wires its own [MockWebServer]
 * + [V2VisitsApi] rather than reusing [PagosE2ETestBase]'s pagos-specific
 * `V2PaymentsApi` wiring — it still reuses the base's in-memory Room +
 * WorkManager-test-mode scaffolding.
 *
 * This exercises the exact production path an app upgrade would run:
 * `enqueuePendingVisitsWorker` → constraint tracking → `WorkerFactory` →
 * `PendingVisitsWorker.doWork()` → `V2VisitsApi.crearVisita`. Invariant under
 * test: `GUARDADO_EN_MICROSIP` flips to `1` ONLY once the server is
 * confirmed to hold the visita (a 2xx); a network failure must leave the row
 * untouched so the durable queue keeps retrying instead of losing it.
 */
@RunWith(AndroidJUnit4::class)
class PendingVisitsWorkerE2ETest {

    private lateinit var context: Context
    private lateinit var mockWebServer: MockWebServer
    private lateinit var v2Api: V2VisitsApi
    private lateinit var testDriver: TestDriver
    private lateinit var visitsStore: VisitsLocalDataSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        AppDatabase.setInstanceForTesting(db)

        mockWebServer = MockWebServer()
        mockWebServer.start()

        v2Api = buildV2VisitsApi(mockWebServer)
        visitsStore = VisitsLocalDataSource(context)

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? {
                if (workerClassName != PendingVisitsWorker::class.java.name) return null
                return PendingVisitsWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    visitsStore = VisitsLocalDataSource(appContext),
                    v2Api = v2Api,
                    useV2 = true
                )
            }
        }

        val wmConfiguration = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, wmConfiguration)
        testDriver = WorkManagerTestInitHelper.getTestDriver(context)
            ?: error("WorkManagerTestInitHelper.getTestDriver returned null after init")
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        AppDatabase.clearInstance()
    }

    private fun buildV2VisitsApi(server: MockWebServer): V2VisitsApi {
        val gson = GsonBuilder().create()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return retrofit.create(V2VisitsApi::class.java)
    }

    private fun uniqueWorkName(visitId: String) = "sync_pending_visit_$visitId"

    private suspend fun currentWorkInfo(visitId: String): WorkInfo? =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(uniqueWorkName(visitId))
            .first()
            .firstOrNull()

    private suspend fun currentWorkId(visitId: String): UUID =
        currentWorkInfo(visitId)?.id ?: error("no WorkInfo enqueued yet for visita $visitId")

    private suspend fun awaitWorkInfo(
        visitId: String,
        timeoutMillis: Long = 20_000,
        pollMillis: Long = 150,
        until: (WorkInfo) -> Boolean
    ): WorkInfo {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: WorkInfo? = null
        while (System.currentTimeMillis() < deadline) {
            val info = currentWorkInfo(visitId)
            if (info != null) {
                last = info
                if (until(info)) return info
            }
            delay(pollMillis)
        }
        return last ?: error("no WorkInfo observed for visita $visitId within ${timeoutMillis}ms")
    }

    /** A pending visita awaiting upload. Realistic Mexican-Spanish cobranza attribution. */
    private fun pendingVisit(id: String) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Ricardo Flores Mendoza",
        COBRADOR_ID = 7,
        FECHA = "2026-07-20T09:30:00Z",
        FORMA_COBRO_ID = 0,
        LAT = 18.4523,
        LNG = -97.3921,
        NOTA = "Cliente prometio pagar la proxima semana",
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21,
        IMPTE_DOCTO_CC_ID = 5000,
        GUARDADO_EN_MICROSIP = 0
    )

    // ── 1. real scheduler happy path: 201 → SUCCEEDED, GUARDADO=1 ──────────

    @Test
    fun happyPath_realSchedulerUploadsAndMarksDone() = runBlocking {
        val visitId = "visita-e2e-happy-001"
        visitsStore.saveVisit(pendingVisit(visitId))

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"id":"$visitId"}""")
        )

        enqueuePendingVisitsWorker(context, visitId, replace = true)
        testDriver.setAllConstraintsMet(currentWorkId(visitId))

        val info = awaitWorkInfo(visitId) {
            it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED
        }
        assertEquals("worker should end SUCCEEDED on a 201", WorkInfo.State.SUCCEEDED, info.state)

        assertEquals(1, mockWebServer.requestCount)
        val request = mockWebServer.takeRequest()
        assertEquals("/v2/visitas", request.path)
        assertEquals(visitId, request.getHeader("Idempotency-Key"))

        val row = visitsStore.getVisitById(visitId)
        assertEquals("row must be marked GUARDADO_EN_MICROSIP=1", 1, row.GUARDADO_EN_MICROSIP)
    }

    // ── 2. network down: IOException → retry, visita never marked done ─────

    @Test
    fun networkDown_retriesAndNeverMarksVisitDone() = runBlocking {
        val visitId = "visita-e2e-networkdown-002"
        visitsStore.saveVisit(pendingVisit(visitId))

        // Simulates the phone losing connectivity mid-upload: the TCP
        // connection drops before any HTTP response is produced, which
        // Retrofit/OkHttp surface to the worker as an IOException.
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        enqueuePendingVisitsWorker(context, visitId, replace = true)
        testDriver.setAllConstraintsMet(currentWorkId(visitId))

        val info = awaitWorkInfo(visitId) {
            it.state == WorkInfo.State.SUCCEEDED ||
                it.state == WorkInfo.State.FAILED ||
                (it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount >= 1)
        }

        assertNotEquals(
            "a network failure must never be treated as a terminal failure — the queue retries",
            WorkInfo.State.FAILED,
            info.state
        )
        assertEquals(
            "worker must go back to ENQUEUED (scheduled retry), not succeed",
            WorkInfo.State.ENQUEUED,
            info.state
        )
        assertTrue("must have actually attempted the run", info.runAttemptCount >= 1)

        val row = visitsStore.getVisitById(visitId)
        assertFalse(
            "server never confirmed the visita — GUARDADO_EN_MICROSIP must stay 0",
            row.GUARDADO_EN_MICROSIP == 1
        )
    }

    // ── 3. duplicate 200 (idempotent resend): both succeed, same key ───────

    @Test
    fun duplicateSuccessfulUpload_isIdempotentAndSafe() = runBlocking {
        val visitId = "visita-e2e-duplicate-003"
        visitsStore.saveVisit(pendingVisit(visitId))

        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"id":"$visitId"}""")
        )
        enqueuePendingVisitsWorker(context, visitId, replace = true)
        testDriver.setAllConstraintsMet(currentWorkId(visitId))
        val firstInfo = awaitWorkInfo(visitId) {
            it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED
        }
        assertEquals(WorkInfo.State.SUCCEEDED, firstInfo.state)
        assertEquals(1, visitsStore.getVisitById(visitId).GUARDADO_EN_MICROSIP)

        // Re-run the exact same visita (e.g. a stray re-enqueue, or a device
        // that lost the ack of its own successful upload). The server would
        // return 200 again (idempotent by body.id); the client must not
        // crash or corrupt state — the visita ends marked done exactly once.
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"id":"$visitId"}""")
        )
        enqueuePendingVisitsWorker(context, visitId, replace = true)
        testDriver.setAllConstraintsMet(currentWorkId(visitId))
        val secondInfo = awaitWorkInfo(visitId) {
            it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED
        }
        assertEquals(WorkInfo.State.SUCCEEDED, secondInfo.state)

        assertEquals(2, mockWebServer.requestCount)
        val firstRequest = mockWebServer.takeRequest()
        val secondRequest = mockWebServer.takeRequest()
        assertEquals(visitId, firstRequest.getHeader("Idempotency-Key"))
        assertEquals(visitId, secondRequest.getHeader("Idempotency-Key"))

        assertEquals(1, visitsStore.getVisitById(visitId).GUARDADO_EN_MICROSIP)
    }
}
