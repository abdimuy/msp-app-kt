package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.GuaranteesWorkEnqueuer
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingWorkSynchronizer
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.SyncAllPendingWorkUseCase.Companion.MAX_ITEMS_PER_SYNC
import com.example.msp_app.core.database.entities.GuaranteeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GuaranteesPendingSynchronizer(
    private val fetchPending: suspend () -> List<GuaranteeEntity>,
    private val enqueuer: GuaranteesWorkEnqueuer
) : PendingWorkSynchronizer {

    override val name: String = NAME

    override suspend fun sync(context: SyncContext): SyncResult = withContext(Dispatchers.IO) {
        val pending = fetchPending()
        if (pending.isEmpty()) return@withContext SyncResult.NothingPending

        val capped = pending.take(MAX_ITEMS_PER_SYNC)
        var successCount = 0
        capped.forEach { guarantee ->
            runCatching { enqueuer.enqueue(guarantee.EXTERNAL_ID, replace = true) }
                .onSuccess { successCount++ }
        }
        SyncResult.Enqueued(itemCount = capped.size, workRequestCount = successCount)
    }

    companion object {
        const val NAME: String = "GUARANTEES"
    }
}
