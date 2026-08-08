package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.entities.VisitEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VisitsPendingSynchronizerTest {

    private val ctx = SyncContext(userId = "u1", userEmail = "u@example.com")

    @Test
    fun `empty pending returns NothingPending`() = runTest {
        val sync = VisitsPendingSynchronizer(
            fetchPending = { emptyList() },
            enqueuer = RecordingEnqueuer()
        )
        assertEquals(SyncResult.NothingPending, sync.sync(ctx))
    }

    @Test
    fun `enqueues each pending visit`() = runTest {
        val enqueuer = RecordingEnqueuer()
        val sync = VisitsPendingSynchronizer(
            fetchPending = { listOf(visitWithId("v1"), visitWithId("v2"), visitWithId("v3")) },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 3, workRequestCount = 3), result)
        assertEquals(listOf("v1", "v2", "v3"), enqueuer.calls)
    }

    @Test
    fun `caps at 50`() = runTest {
        val visits = (1..60).map { visitWithId("v$it") }
        val enqueuer = RecordingEnqueuer()
        val sync = VisitsPendingSynchronizer(
            fetchPending = { visits },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 50, workRequestCount = 50), result)
    }

    @Test
    fun `partial failure counted accurately`() = runTest {
        val enqueuer = RecordingEnqueuer(failingIds = setOf("v1"))
        val sync = VisitsPendingSynchronizer(
            fetchPending = { listOf(visitWithId("v1"), visitWithId("v2")) },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 2, workRequestCount = 1), result)
    }

    private fun visitWithId(id: String) = VisitEntity(
        ID = id,
        CLIENTE_ID = 0,
        COBRADOR = "",
        COBRADOR_ID = 0,
        FECHA = "2026-04-15",
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        NOTA = null,
        TIPO_VISITA = "REGULAR",
        ZONA_CLIENTE_ID = 0,
        IMPTE_DOCTO_CC_ID = 0,
        GUARDADO_EN_MICROSIP = 0
    )

    private class RecordingEnqueuer(
        private val failingIds: Set<String> = emptySet()
    ) : VisitsWorkEnqueuer {
        val calls: MutableList<String> = mutableListOf()

        override fun enqueue(visitId: String, replace: Boolean) {
            calls += visitId
            if (visitId in failingIds) throw RuntimeException("boom")
        }
    }
}
