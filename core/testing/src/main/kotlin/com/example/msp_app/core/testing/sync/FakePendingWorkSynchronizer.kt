package com.example.msp_app.core.testing.sync

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingWorkSynchronizer
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Recording fake of [PendingWorkSynchronizer] with a programmable outcome.
 *
 * Pass [result] for a synchronizer that acks successfully (or reports
 * [SyncResult.NothingPending] / [SyncResult.Skipped]), or [throwable] for one
 * that fails. `SyncAllPendingWorkUseCase.execute` wraps every call in
 * `runCatching`, so a throwing fake exercises the exact same path a real
 * failing synchronizer (e.g. a network error mid-upload) would.
 */
class FakePendingWorkSynchronizer(
    override val name: String,
    private val result: SyncResult = SyncResult.NothingPending,
    private val throwable: Throwable? = null
) : PendingWorkSynchronizer {

    val callCount = AtomicInteger(0)

    private val mutableContextsSeen: MutableList<SyncContext> = Collections.synchronizedList(
        mutableListOf()
    )

    val contextsSeen: List<SyncContext>
        get() = mutableContextsSeen.toList()

    override suspend fun sync(context: SyncContext): SyncResult {
        callCount.incrementAndGet()
        mutableContextsSeen += context
        throwable?.let { throw it }
        return result
    }
}
