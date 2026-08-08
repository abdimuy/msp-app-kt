package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingWorkSynchronizer
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SessionSyncGate
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SessionSyncObserver
import java.util.Collections
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncAllPendingWorkUseCaseTest {

    private val ctx = SyncContext(userId = "user-1", userEmail = "u@example.com")

    @Test
    fun `execute runs synchronizers in parallel`() = runTest {
        val sync1 = DelayingSynchronizer("S1", delayMillis = 1_000L)
        val sync2 = DelayingSynchronizer("S2", delayMillis = 1_000L)
        val sync3 = DelayingSynchronizer("S3", delayMillis = 1_000L)
        val observer = RecordingObserver()
        val useCase = SyncAllPendingWorkUseCase(
            synchronizers = listOf(sync1, sync2, sync3),
            gate = AlwaysOpenGate(),
            observer = observer
        )

        val start = currentTime
        val result = useCase.execute(ctx)
        val elapsed = currentTime - start

        assertEquals(3, result.size)
        // Parallel: total ≈ single delay, not 3×
        assertTrue("Expected parallel execution, elapsed=$elapsed", elapsed < 2_500L)
    }

    @Test
    fun `execute isolates per-synchronizer failures`() = runTest {
        val good = FakeSynchronizer(
            "GOOD",
            SyncResult.Enqueued(itemCount = 2, workRequestCount = 2)
        )
        val boom = RuntimeException("boom")
        val bad = ThrowingSynchronizer("BAD", boom)
        val untouched = FakeSynchronizer("OTHER", SyncResult.NothingPending)

        val useCase = SyncAllPendingWorkUseCase(
            synchronizers = listOf(good, bad, untouched),
            gate = AlwaysOpenGate(),
            observer = NullObserver
        )

        val result = useCase.execute(ctx)

        assertEquals(3, result.size)
        assertEquals(
            SyncResult.Enqueued(itemCount = 2, workRequestCount = 2),
            result["GOOD"]
        )
        val failed = result["BAD"]
        assertTrue(failed is SyncResult.Failed)
        assertSame(boom, (failed as SyncResult.Failed).cause)
        assertEquals(SyncResult.NothingPending, result["OTHER"])
    }

    @Test
    fun `global timeout returns empty map`() = runTest {
        val slow = DelayingSynchronizer("SLOW", delayMillis = 90_000L)

        val useCase = SyncAllPendingWorkUseCase(
            synchronizers = listOf(slow),
            gate = AlwaysOpenGate(),
            observer = NullObserver,
            timeoutMillis = 60_000L
        )

        val result = useCase.execute(ctx)
        assertTrue("Expected empty map on timeout", result.isEmpty())
    }

    @Test
    fun `gate returning false short-circuits without touching synchronizers`() = runTest {
        val spy = FakeSynchronizer("S", SyncResult.NothingPending)
        val gate = ClosedGate()

        val useCase = SyncAllPendingWorkUseCase(
            synchronizers = listOf(spy),
            gate = gate,
            observer = NullObserver
        )

        val result = useCase.execute(ctx)

        assertTrue(result.isEmpty())
        assertEquals(0, spy.calls)
    }

    @Test
    fun `SyncContext propagates to synchronizers including null email`() = runTest {
        val capturing = CapturingSynchronizer("CAP")

        val useCase = SyncAllPendingWorkUseCase(
            synchronizers = listOf(capturing),
            gate = AlwaysOpenGate(),
            observer = NullObserver
        )

        val nullEmailCtx = SyncContext(userId = "u1", userEmail = null)
        useCase.execute(nullEmailCtx)

        val received = capturing.lastContext
        assertNotNull(received)
        assertEquals("u1", received!!.userId)
        assertEquals(null, received.userEmail)
    }

    @Test
    fun `observer is notified for every synchronizer including failures`() = runTest {
        val s1 = FakeSynchronizer("A", SyncResult.NothingPending)
        val s2 = ThrowingSynchronizer("B", RuntimeException("boom"))
        val s3 = FakeSynchronizer(
            "C",
            SyncResult.Enqueued(itemCount = 1, workRequestCount = 1)
        )
        val observer = RecordingObserver()

        val useCase = SyncAllPendingWorkUseCase(
            synchronizers = listOf(s1, s2, s3),
            gate = AlwaysOpenGate(),
            observer = observer
        )

        useCase.execute(ctx)

        val byName = observer.records.associate { it.first to it.second }
        assertEquals(3, byName.size)
        assertEquals(SyncResult.NothingPending, byName["A"])
        assertTrue(byName["B"] is SyncResult.Failed)
        assertEquals(
            SyncResult.Enqueued(itemCount = 1, workRequestCount = 1),
            byName["C"]
        )
    }

    @Test
    fun `flaky observer does not tumble the flow`() = runTest {
        val sync = FakeSynchronizer("S", SyncResult.NothingPending)
        val observer = object : SessionSyncObserver {
            override fun onResult(synchronizerName: String, result: SyncResult) {
                throw RuntimeException("observer explodes")
            }
        }

        val useCase = SyncAllPendingWorkUseCase(
            synchronizers = listOf(sync),
            gate = AlwaysOpenGate(),
            observer = observer
        )

        // Should not throw
        val result = useCase.execute(ctx)
        assertEquals(1, result.size)
    }

    // --- fakes ---

    private class FakeSynchronizer(
        override val name: String,
        private val result: SyncResult
    ) : PendingWorkSynchronizer {
        var calls = 0
        override suspend fun sync(context: SyncContext): SyncResult {
            calls++
            return result
        }
    }

    private class ThrowingSynchronizer(
        override val name: String,
        private val cause: Throwable
    ) : PendingWorkSynchronizer {
        override suspend fun sync(context: SyncContext): SyncResult {
            throw cause
        }
    }

    private class DelayingSynchronizer(
        override val name: String,
        private val delayMillis: Long
    ) : PendingWorkSynchronizer {
        override suspend fun sync(context: SyncContext): SyncResult {
            delay(delayMillis)
            return SyncResult.NothingPending
        }
    }

    private class CapturingSynchronizer(override val name: String) : PendingWorkSynchronizer {
        var lastContext: SyncContext? = null
        override suspend fun sync(context: SyncContext): SyncResult {
            lastContext = context
            return SyncResult.NothingPending
        }
    }

    private class AlwaysOpenGate : SessionSyncGate {
        override fun markIfNotSynced(userId: String): Boolean = true
    }

    private class ClosedGate : SessionSyncGate {
        override fun markIfNotSynced(userId: String): Boolean = false
    }

    private class RecordingObserver : SessionSyncObserver {
        val records: MutableList<Pair<String, SyncResult>> =
            Collections.synchronizedList(mutableListOf())

        override fun onResult(synchronizerName: String, result: SyncResult) {
            records += synchronizerName to result
        }
    }

    private object NullObserver : SessionSyncObserver {
        override fun onResult(synchronizerName: String, result: SyncResult) = Unit
    }
}
