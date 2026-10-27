package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VisitsPendingSynchronizerTest {

    private val ctx = SyncContext(userId = "u1", userEmail = "u@example.com")

    @Test
    fun `empty pending returns NothingPending`() = runTest {
        val sync = VisitsPendingSynchronizer(
            fetchPendingIds = { emptyList() },
            enqueuer = RecordingEnqueuer()
        )
        assertEquals(SyncResult.NothingPending, sync.sync(ctx))
    }

    @Test
    fun `enqueues each pending visit with KEEP policy`() = runTest {
        val enqueuer = RecordingEnqueuer()
        val sync = VisitsPendingSynchronizer(
            fetchPendingIds = { listOf("v1", "v2", "v3") },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 3, workRequestCount = 3), result)
        assertEquals(listOf("v1", "v2", "v3"), enqueuer.calls)
        // KEEP is no longer a runtime choice to assert on: VisitsWorkEnqueuer
        // no longer has a `replace` parameter, and enqueuePendingVisitsWorker
        // hardcodes ExistingWorkPolicy.KEEP internally — REPLACE is not
        // reachable from this call at all (see SyncAllPendingWorkUseCase KDoc).
    }

    /** E-APP-072 (plan visitas P1, Tarea 12): sin tope de 50, en el orden recibido. */
    @Test
    fun `E-APP-072 encola las 60 sin tope y en el orden recibido`() = runTest {
        val ids = (1..60).map { "v%02d".format(it) }.reversed()
        val enqueuer = RecordingEnqueuer()
        val sync = VisitsPendingSynchronizer(
            fetchPendingIds = { ids },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 60, workRequestCount = 60), result)
        assertEquals(ids, enqueuer.calls)
    }

    @Test
    fun `partial failure counted accurately`() = runTest {
        val enqueuer = RecordingEnqueuer(failingIds = setOf("v1"))
        val sync = VisitsPendingSynchronizer(
            fetchPendingIds = { listOf("v1", "v2") },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 2, workRequestCount = 1), result)
    }

    private class RecordingEnqueuer(
        private val failingIds: Set<String> = emptySet()
    ) : VisitsWorkEnqueuer {
        val calls: MutableList<String> = mutableListOf()

        override fun enqueue(visitId: String) {
            calls += visitId
            if (visitId in failingIds) throw RuntimeException("boom")
        }
    }
}
