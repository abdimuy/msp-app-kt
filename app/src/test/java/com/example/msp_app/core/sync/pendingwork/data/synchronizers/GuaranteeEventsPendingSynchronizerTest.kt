package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.sync.pendingwork.domain.ports.GuaranteeEventsWorkEnqueuer
import com.example.msp_app.data.local.entities.GuaranteeEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GuaranteeEventsPendingSynchronizerTest {

    private val ctx = SyncContext(userId = "u1", userEmail = "u@example.com")

    @Test
    fun `empty pending returns NothingPending`() = runTest {
        val enqueuer = RecordingEnqueuer()
        val sync = GuaranteeEventsPendingSynchronizer(
            fetchPending = { emptyList() },
            enqueuer = enqueuer
        )

        assertEquals(SyncResult.NothingPending, sync.sync(ctx))
        assertEquals(0, enqueuer.calls)
    }

    @Test
    fun `5 pending events batch-enqueue as single work request`() = runTest {
        val enqueuer = RecordingEnqueuer()
        val events = (1..5).map { eventWithId("e$it") }
        val sync = GuaranteeEventsPendingSynchronizer(
            fetchPending = { events },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 5, workRequestCount = 1), result)
        assertEquals(1, enqueuer.calls)
        assertEquals(true, enqueuer.lastReplace)
    }

    @Test
    fun `caps itemCount at 50 even though single work request`() = runTest {
        val enqueuer = RecordingEnqueuer()
        val events = (1..80).map { eventWithId("e$it") }
        val sync = GuaranteeEventsPendingSynchronizer(
            fetchPending = { events },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 50, workRequestCount = 1), result)
    }

    @Test
    fun `enqueue failure reports workRequestCount 0`() = runTest {
        val enqueuer = RecordingEnqueuer(shouldFail = true)
        val sync = GuaranteeEventsPendingSynchronizer(
            fetchPending = { listOf(eventWithId("e1")) },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 1, workRequestCount = 0), result)
    }

    private fun eventWithId(id: String) = GuaranteeEventEntity(
        ID = id,
        GARANTIA_ID = "g1",
        TIPO_EVENTO = "SOLICITUD",
        FECHA_EVENTO = "2026-04-15",
        COMENTARIO = null,
        ENVIADO = 0
    )

    private class RecordingEnqueuer(
        private val shouldFail: Boolean = false
    ) : GuaranteeEventsWorkEnqueuer {
        var calls: Int = 0
        var lastReplace: Boolean = false

        override fun enqueue(replace: Boolean) {
            calls++
            lastReplace = replace
            if (shouldFail) throw RuntimeException("boom")
        }
    }
}
