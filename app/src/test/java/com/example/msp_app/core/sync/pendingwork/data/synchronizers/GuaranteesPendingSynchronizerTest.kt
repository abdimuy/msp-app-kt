package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.GuaranteesWorkEnqueuer
import com.example.msp_app.core.database.entities.GuaranteeEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GuaranteesPendingSynchronizerTest {

    private val ctx = SyncContext(userId = "u1", userEmail = "u@example.com")

    @Test
    fun `empty pending returns NothingPending`() = runTest {
        val sync = GuaranteesPendingSynchronizer(
            fetchPending = { emptyList() },
            enqueuer = RecordingEnqueuer()
        )
        assertEquals(SyncResult.NothingPending, sync.sync(ctx))
    }

    @Test
    fun `enqueues pending guarantees by EXTERNAL_ID`() = runTest {
        val enqueuer = RecordingEnqueuer()
        val sync = GuaranteesPendingSynchronizer(
            fetchPending = {
                listOf(
                    guaranteeWithExternal("ext-1"),
                    guaranteeWithExternal("ext-2")
                )
            },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 2, workRequestCount = 2), result)
        assertEquals(listOf("ext-1", "ext-2"), enqueuer.calls)
    }

    @Test
    fun `caps at 50`() = runTest {
        val guarantees = (1..70).map { guaranteeWithExternal("ext-$it") }
        val enqueuer = RecordingEnqueuer()
        val sync = GuaranteesPendingSynchronizer(
            fetchPending = { guarantees },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 50, workRequestCount = 50), result)
    }

    @Test
    fun `failure on one guarantee does not abort the rest`() = runTest {
        val enqueuer = RecordingEnqueuer(failingIds = setOf("ext-2"))
        val sync = GuaranteesPendingSynchronizer(
            fetchPending = {
                listOf(
                    guaranteeWithExternal("ext-1"),
                    guaranteeWithExternal("ext-2"),
                    guaranteeWithExternal("ext-3")
                )
            },
            enqueuer = enqueuer
        )

        val result = sync.sync(ctx)

        assertEquals(SyncResult.Enqueued(itemCount = 3, workRequestCount = 2), result)
    }

    private fun guaranteeWithExternal(externalId: String) = GuaranteeEntity(
        ID = 0,
        EXTERNAL_ID = externalId,
        DOCTO_CC_ID = null,
        ESTADO = "PENDIENTE",
        DESCRIPCION_FALLA = "",
        OBSERVACIONES = null,
        UPLOADED = 0,
        FECHA_SOLICITUD = "2026-04-15",
        NOMBRE_CLIENTE = null,
        NOMBRE_PRODUCTO = null
    )

    private class RecordingEnqueuer(
        private val failingIds: Set<String> = emptySet()
    ) : GuaranteesWorkEnqueuer {
        val calls: MutableList<String> = mutableListOf()

        override fun enqueue(guaranteeExternalId: String, replace: Boolean) {
            calls += guaranteeExternalId
            if (guaranteeExternalId in failingIds) throw RuntimeException("boom")
        }
    }
}
