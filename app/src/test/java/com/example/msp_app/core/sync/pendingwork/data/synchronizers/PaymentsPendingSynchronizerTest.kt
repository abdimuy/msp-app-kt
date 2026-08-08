package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PaymentsWorkEnqueuer
import com.example.msp_app.core.database.entities.PaymentEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentsPendingSynchronizerTest {

    private val ctx = SyncContext(userId = "u1", userEmail = "u@example.com")

    @Test
    fun `empty pending returns NothingPending`() = runTest {
        val sync = PaymentsPendingSynchronizer(
            fetchPending = { emptyList() },
            enqueuer = RecordingEnqueuer()
        )
        assertEquals(SyncResult.NothingPending, sync.sync(ctx))
    }

    @Test
    fun `enqueues all pending with REPLACE policy`() = runTest {
        val enqueuer = RecordingEnqueuer()
        val sync = PaymentsPendingSynchronizer(
            fetchPending = { listOf(paymentWithId("p1"), paymentWithId("p2")) },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 2, workRequestCount = 2), result)
        assertEquals(listOf("p1" to true, "p2" to true), enqueuer.calls)
    }

    @Test
    fun `caps at 50`() = runTest {
        val payments = (1..60).map { paymentWithId("p$it") }
        val enqueuer = RecordingEnqueuer()
        val sync = PaymentsPendingSynchronizer(
            fetchPending = { payments },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 50, workRequestCount = 50), result)
        assertEquals(50, enqueuer.calls.size)
    }

    @Test
    fun `failure on one item does not abort others`() = runTest {
        val enqueuer = RecordingEnqueuer(failingIds = setOf("p2"))
        val sync = PaymentsPendingSynchronizer(
            fetchPending = {
                listOf(
                    paymentWithId("p1"),
                    paymentWithId("p2"),
                    paymentWithId("p3")
                )
            },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 3, workRequestCount = 2), result)
    }

    private fun paymentWithId(id: String) = PaymentEntity(
        ID = id,
        COBRADOR = "",
        DOCTO_CC_ACR_ID = 0,
        DOCTO_CC_ID = 0,
        FECHA_HORA_PAGO = "",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 0.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 0,
        COBRADOR_ID = 0,
        FORMA_COBRO_ID = 0,
        ZONA_CLIENTE_ID = 0,
        NOMBRE_CLIENTE = ""
    )

    private class RecordingEnqueuer(
        private val failingIds: Set<String> = emptySet()
    ) : PaymentsWorkEnqueuer {
        val calls: MutableList<Pair<String, Boolean>> = mutableListOf()

        override fun enqueue(paymentId: String, replace: Boolean) {
            calls += paymentId to replace
            if (paymentId in failingIds) throw RuntimeException("boom")
        }
    }
}
