package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.LocalSalesWorkEnqueuer
import com.example.msp_app.data.local.entities.LocalSaleEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSalesPendingSynchronizerTest {

    private val withEmail = SyncContext(userId = "u1", userEmail = "u@example.com")

    @Test
    fun `empty pending returns NothingPending`() = runTest {
        val sync = LocalSalesPendingSynchronizer(
            fetchPending = { emptyList() },
            enqueuer = RecordingEnqueuer()
        )

        assertEquals(SyncResult.NothingPending, sync.sync(withEmail))
    }

    @Test
    fun `three pending enqueues all three with REPLACE policy`() = runTest {
        val enqueuer = RecordingEnqueuer()
        val sync = LocalSalesPendingSynchronizer(
            fetchPending = { listOf(saleWithId("s1"), saleWithId("s2"), saleWithId("s3")) },
            enqueuer = enqueuer
        )

        val result = sync.sync(withEmail)

        assertEquals(SyncResult.Enqueued(itemCount = 3, workRequestCount = 3), result)
        assertEquals(listOf("s1", "s2", "s3"), enqueuer.calls.map { it.saleId })
        assertTrue(enqueuer.calls.all { it.replace })
        assertTrue(enqueuer.calls.all { it.email == "u@example.com" })
    }

    @Test
    fun `caps at 50 items`() = runTest {
        val sales = (1..60).map { saleWithId("s$it") }
        val enqueuer = RecordingEnqueuer()
        val sync = LocalSalesPendingSynchronizer(
            fetchPending = { sales },
            enqueuer = enqueuer
        )

        val result = sync.sync(withEmail)

        assertEquals(SyncResult.Enqueued(itemCount = 50, workRequestCount = 50), result)
        assertEquals(50, enqueuer.calls.size)
    }

    @Test
    fun `null email returns Skipped`() = runTest {
        val enqueuer = RecordingEnqueuer()
        val sync = LocalSalesPendingSynchronizer(
            fetchPending = { listOf(saleWithId("s1")) },
            enqueuer = enqueuer
        )

        val result = sync.sync(SyncContext(userId = "u1", userEmail = null))

        assertEquals(SyncResult.Skipped, result)
        assertTrue(enqueuer.calls.isEmpty())
    }

    @Test
    fun `blank email returns Skipped`() = runTest {
        val enqueuer = RecordingEnqueuer()
        val sync = LocalSalesPendingSynchronizer(
            fetchPending = { listOf(saleWithId("s1")) },
            enqueuer = enqueuer
        )

        val result = sync.sync(SyncContext(userId = "u1", userEmail = ""))

        assertEquals(SyncResult.Skipped, result)
        assertTrue(enqueuer.calls.isEmpty())
    }

    @Test
    fun `failure on one item does not abort the rest`() = runTest {
        val enqueuer = RecordingEnqueuer(failingIds = setOf("s2"))
        val sync = LocalSalesPendingSynchronizer(
            fetchPending = { listOf(saleWithId("s1"), saleWithId("s2"), saleWithId("s3")) },
            enqueuer = enqueuer
        )

        val result = sync.sync(withEmail)

        assertEquals(SyncResult.Enqueued(itemCount = 3, workRequestCount = 2), result)
        assertEquals(3, enqueuer.calls.size) // every id was attempted
    }

    private fun saleWithId(id: String) = LocalSaleEntity(
        LOCAL_SALE_ID = id,
        NOMBRE_CLIENTE = "N",
        FECHA_VENTA = "2026-04-15",
        LATITUD = 0.0,
        LONGITUD = 0.0,
        DIRECCION = "",
        PARCIALIDAD = 0.0,
        ENGANCHE = null,
        TELEFONO = "",
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = null,
        NOTA = null,
        DIA_COBRANZA = "LUN",
        PRECIO_TOTAL = 0.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        MONTO_DE_CONTADO = 0.0,
        ENVIADO = false
    )

    private class RecordingEnqueuer(
        private val failingIds: Set<String> = emptySet()
    ) : LocalSalesWorkEnqueuer {
        data class Call(val saleId: String, val email: String, val replace: Boolean)

        val calls: MutableList<Call> = mutableListOf()

        override fun enqueue(localSaleId: String, userEmail: String, replace: Boolean) {
            calls += Call(localSaleId, userEmail, replace)
            if (localSaleId in failingIds) throw RuntimeException("boom for $localSaleId")
        }
    }
}
