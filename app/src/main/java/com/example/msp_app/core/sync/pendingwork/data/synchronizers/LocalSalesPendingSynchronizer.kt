package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.LocalSalesWorkEnqueuer
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingWorkSynchronizer
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.SyncAllPendingWorkUseCase.Companion.MAX_ITEMS_PER_SYNC
import com.example.msp_app.data.local.entities.LocalSaleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalSalesPendingSynchronizer(
    private val fetchPending: suspend () -> List<LocalSaleEntity>,
    private val enqueuer: LocalSalesWorkEnqueuer
) : PendingWorkSynchronizer {

    override val name: String = NAME

    override suspend fun sync(context: SyncContext): SyncResult = withContext(Dispatchers.IO) {
        val pending = fetchPending()
        if (pending.isEmpty()) return@withContext SyncResult.NothingPending

        val email = context.userEmail
        if (email.isNullOrBlank()) return@withContext SyncResult.Skipped

        val capped = pending.take(MAX_ITEMS_PER_SYNC)
        var successCount = 0
        capped.forEach { sale ->
            runCatching { enqueuer.enqueue(sale.LOCAL_SALE_ID, email, replace = true) }
                .onSuccess { successCount++ }
        }
        SyncResult.Enqueued(itemCount = capped.size, workRequestCount = successCount)
    }

    companion object {
        const val NAME: String = "LOCAL_SALES"
    }
}
