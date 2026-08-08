package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.GuaranteeEventsWorkEnqueuer
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingWorkSynchronizer
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.SyncAllPendingWorkUseCase.Companion.MAX_ITEMS_PER_SYNC
import com.example.msp_app.core.database.entities.GuaranteeEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GuaranteeEventsPendingSynchronizer(
    private val fetchPending: suspend () -> List<GuaranteeEventEntity>,
    private val enqueuer: GuaranteeEventsWorkEnqueuer
) : PendingWorkSynchronizer {

    override val name: String = NAME

    override suspend fun sync(context: SyncContext): SyncResult = withContext(Dispatchers.IO) {
        val pending = fetchPending()
        if (pending.isEmpty()) return@withContext SyncResult.NothingPending

        val capped = pending.take(MAX_ITEMS_PER_SYNC)
        val enqueued = runCatching { enqueuer.enqueue(replace = true) }.isSuccess
        SyncResult.Enqueued(
            itemCount = capped.size,
            workRequestCount = if (enqueued) 1 else 0
        )
    }

    companion object {
        const val NAME: String = "GUARANTEE_EVENTS"
    }
}
