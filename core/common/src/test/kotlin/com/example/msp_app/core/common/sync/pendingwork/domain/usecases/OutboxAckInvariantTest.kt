package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SessionSyncGate
import com.example.msp_app.core.testing.sync.FakePendingWorkSynchronizer
import com.example.msp_app.core.testing.sync.RecordingSessionSyncObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Explicit contract test for the outbox ack invariant (spec §7.1 / §13), now
 * that the pendingwork domain lives in `:core:common` as the template every
 * future synchronizer (visits, guarantees, sales, ...) is built on. This does
 * NOT change [SyncAllPendingWorkUseCase] behavior — it pins down, with
 * reusable recording fakes from `:core:testing`, the invariants
 * [SyncAllPendingWorkUseCaseTest] already exercised inline:
 *
 *  1. An item is never reported as confirmed ([SyncResult.Enqueued]) without
 *     the synchronizer actually acking it: a synchronizer that throws always
 *     surfaces as [SyncResult.Failed], never as [SyncResult.Enqueued].
 *  2. A synchronizer that fails does not tumble the others: every other
 *     synchronizer still runs to completion and reports its real result.
 *  3. The observer receives exactly one [SyncResult] per synchronizer, no
 *     more, no less, regardless of the success/failure mix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OutboxAckInvariantTest {

    private val ctx = SyncContext(userId = "user-1", userEmail = "u@example.com")

    private val alwaysOpenGate = object : SessionSyncGate {
        override fun markIfNotSynced(userId: String): Boolean = true
    }

    @Test
    fun `a throwing synchronizer is never reported as confirmed, only as failed`() = runTest {
        val failing = FakePendingWorkSynchronizer(
            name = "PAYMENTS",
            throwable = IllegalStateException("boom")
        )
        val observer = RecordingSessionSyncObserver()
        val useCase = SyncAllPendingWorkUseCase(
            synchronizers = listOf(failing),
            gate = alwaysOpenGate,
            observer = observer
        )

        useCase.execute(ctx)

        val record = observer.records.single()
        assertEquals("PAYMENTS", record.synchronizerName)
        assertTrue(
            "un synchronizer que lanza jamás debe reportarse como Enqueued",
            record.result is SyncResult.Failed
        )
        assertFalse(record.result is SyncResult.Enqueued)
    }

    @Test
    fun `a failing synchronizer does not tumble the others`() = runTest {
        val visits = FakePendingWorkSynchronizer(
            name = "VISITS",
            result = SyncResult.Enqueued(itemCount = 3, workRequestCount = 3)
        )
        val guarantees = FakePendingWorkSynchronizer(
            name = "GUARANTEES",
            throwable = RuntimeException("boom")
        )
        val localSales =
            FakePendingWorkSynchronizer(name = "LOCAL_SALES", result = SyncResult.NothingPending)
        val observer = RecordingSessionSyncObserver()
        val useCase = SyncAllPendingWorkUseCase(
            synchronizers = listOf(visits, guarantees, localSales),
            gate = alwaysOpenGate,
            observer = observer
        )

        val results = useCase.execute(ctx)

        assertEquals(1, visits.callCount.get())
        assertEquals(1, localSales.callCount.get())
        assertEquals(SyncResult.Enqueued(itemCount = 3, workRequestCount = 3), results["VISITS"])
        assertEquals(SyncResult.NothingPending, results["LOCAL_SALES"])
        assertTrue(results["GUARANTEES"] is SyncResult.Failed)
    }

    @Test
    fun `observer receives exactly one SyncResult per synchronizer`() = runTest {
        val synchronizers = listOf(
            FakePendingWorkSynchronizer(name = "A", result = SyncResult.NothingPending),
            FakePendingWorkSynchronizer(name = "B", throwable = RuntimeException("x")),
            FakePendingWorkSynchronizer(
                name = "C",
                result = SyncResult.Enqueued(itemCount = 1, workRequestCount = 1)
            ),
            FakePendingWorkSynchronizer(name = "D", result = SyncResult.Skipped),
            FakePendingWorkSynchronizer(name = "E", result = SyncResult.NothingPending)
        )
        val observer = RecordingSessionSyncObserver()
        val useCase = SyncAllPendingWorkUseCase(
            synchronizers = synchronizers,
            gate = alwaysOpenGate,
            observer = observer
        )

        useCase.execute(ctx)

        assertEquals(synchronizers.size, observer.records.size)
        assertEquals(
            synchronizers.map { it.name }.toSet(),
            observer.records.map { it.synchronizerName }.toSet()
        )
    }
}
