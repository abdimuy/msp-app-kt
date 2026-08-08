package com.example.msp_app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B6 — on-device e2e of [com.example.msp_app.workers.PendingPaymentsWorker]
 * driven by the REAL WorkManager scheduler (test mode), talking to a
 * [okhttp3.mockwebserver.MockWebServer] standing in for msp-api's
 * `POST /v2/cobranza/pagos`.
 *
 * This exercises the exact production path an app upgrade would run:
 * `enqueuePendingPaymentsWorker` → constraint tracking → `WorkerFactory` →
 * `PendingPaymentsWorker.doWork()` → `V2PaymentsApi.crearPago`. Money-loss
 * invariant under test: `GUARDADO_EN_MICROSIP` flips to `true` ONLY once the
 * server is confirmed to hold the pago (a 2xx); a network failure must leave
 * the row untouched so the durable queue keeps retrying instead of losing it.
 */
@RunWith(AndroidJUnit4::class)
class PendingPaymentsWorkerE2ETest : PagosE2ETestBase() {

    private lateinit var paymentsStore: PaymentsLocalDataSource

    private fun ensureStore(): PaymentsLocalDataSource {
        if (!::paymentsStore.isInitialized) {
            paymentsStore = PaymentsLocalDataSource(context)
        }
        return paymentsStore
    }

    /** A pending pago awaiting upload. Realistic Mexican-Spanish cobranza attribution. */
    private fun pendingPayment(id: String) = PaymentEntity(
        ID = id,
        COBRADOR = "Ricardo Flores Mendoza",
        DOCTO_CC_ACR_ID = 5000,
        DOCTO_CC_ID = 6000,
        FECHA_HORA_PAGO = "2026-07-20T09:30:00Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 850.0,
        LAT = 18.4523,
        LNG = -97.3921,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 87327,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Minerva Lopez Garcia"
    )

    // ── 1. real scheduler happy path: 200 → SUCCEEDED, GUARDADO=1 ──────────

    @Test
    fun happyPath_realSchedulerUploadsAndMarksDone() = runBlocking {
        val store = ensureStore()
        val paymentId = "pago-e2e-happy-001"
        store.savePayment(pendingPayment(paymentId))

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"$paymentId","estado":"ok"}""")
        )

        enqueuePendingPaymentsWorker(context, paymentId, replace = true)
        testDriver.setAllConstraintsMet(currentWorkId(paymentId))

        val info = awaitWorkInfo(paymentId) {
            it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED
        }
        assertEquals("worker should end SUCCEEDED on a 200", WorkInfo.State.SUCCEEDED, info.state)

        assertEquals(1, mockWebServer.requestCount)
        val request = mockWebServer.takeRequest()
        assertEquals("/v2/cobranza/pagos", request.path)
        assertEquals(paymentId, request.getHeader("Idempotency-Key"))

        val row = store.getPaymentById(paymentId)
        assertTrue("row must be marked GUARDADO_EN_MICROSIP=true", row!!.GUARDADO_EN_MICROSIP)
    }

    // ── 2. network down: IOException → retry, money never marked done ──────

    @Test
    fun networkDown_retriesAndNeverMarksMoneyDone() = runBlocking {
        val store = ensureStore()
        val paymentId = "pago-e2e-networkdown-002"
        store.savePayment(pendingPayment(paymentId))

        // Simulates the phone losing connectivity mid-upload: the TCP
        // connection drops before any HTTP response is produced, which
        // Retrofit/OkHttp surface to the worker as an IOException.
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        enqueuePendingPaymentsWorker(context, paymentId, replace = true)
        testDriver.setAllConstraintsMet(currentWorkId(paymentId))

        val info = awaitWorkInfo(paymentId) {
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

        val row = store.getPaymentById(paymentId)
        assertFalse(
            "money is not confirmed server-side — GUARDADO_EN_MICROSIP must stay false",
            row!!.GUARDADO_EN_MICROSIP
        )
    }

    // ── 3. duplicate 200 (idempotent resend): both succeed, same key ───────

    @Test
    fun duplicateSuccessfulUpload_isIdempotentAndSafe() = runBlocking {
        val store = ensureStore()
        val paymentId = "pago-e2e-duplicate-003"
        store.savePayment(pendingPayment(paymentId))

        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"id":"$paymentId","estado":"ok"}""")
        )
        enqueuePendingPaymentsWorker(context, paymentId, replace = true)
        testDriver.setAllConstraintsMet(currentWorkId(paymentId))
        val firstInfo = awaitWorkInfo(paymentId) {
            it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED
        }
        assertEquals(WorkInfo.State.SUCCEEDED, firstInfo.state)
        assertTrue(store.getPaymentById(paymentId)!!.GUARDADO_EN_MICROSIP)

        // Re-run the exact same pago (e.g. a stray re-enqueue, or a device
        // that lost the ack of its own successful upload). The server would
        // return 200 again (idempotent by datos.id); the client must not
        // crash or corrupt state.
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"id":"$paymentId","estado":"ok"}""")
        )
        enqueuePendingPaymentsWorker(context, paymentId, replace = true)
        testDriver.setAllConstraintsMet(currentWorkId(paymentId))
        val secondInfo = awaitWorkInfo(paymentId) {
            it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED
        }
        assertEquals(WorkInfo.State.SUCCEEDED, secondInfo.state)

        assertEquals(2, mockWebServer.requestCount)
        val firstRequest = mockWebServer.takeRequest()
        val secondRequest = mockWebServer.takeRequest()
        assertEquals(paymentId, firstRequest.getHeader("Idempotency-Key"))
        assertEquals(paymentId, secondRequest.getHeader("Idempotency-Key"))

        assertTrue(store.getPaymentById(paymentId)!!.GUARDADO_EN_MICROSIP)
    }
}
