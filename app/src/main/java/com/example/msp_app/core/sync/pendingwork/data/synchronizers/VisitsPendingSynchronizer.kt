package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingWorkSynchronizer
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reencola en la sesión **todas** las visitas pendientes, en el orden que da
 * [fetchPendingIds] (`VisitDao.pendientesOrdenadas`: `FECHA ASC, ID ASC`).
 *
 * E-APP-072 (plan visitas P1, Tarea 12; cierra H3): antes tomaba
 * `take(MAX_ITEMS_PER_SYNC)` = 50 de `getPendingVisits`, que no tiene
 * `ORDER BY`: con 60 pendientes salían 50 en orden de inserción y de la 51 en
 * adelante nunca se encolaban en la sesión. El tope existía para "borrar datos
 * y reinstalar"; `enqueuePendingVisitsWorker` usa `KEEP` y nombre único por
 * id, así que encolar todas no amplifica trabajo. `SyncAllPendingWorkUseCase`
 * (y su tope para los otros sincronizadores) no se toca.
 */
class VisitsPendingSynchronizer(
    private val fetchPendingIds: suspend () -> List<String>,
    private val enqueuer: VisitsWorkEnqueuer
) : PendingWorkSynchronizer {

    override val name: String = NAME

    override suspend fun sync(context: SyncContext): SyncResult = withContext(Dispatchers.IO) {
        val pending = fetchPendingIds()
        if (pending.isEmpty()) return@withContext SyncResult.NothingPending

        var successCount = 0
        pending.forEach { id ->
            runCatching { enqueuer.enqueue(id) }
                .onSuccess { successCount++ }
        }
        SyncResult.Enqueued(itemCount = pending.size, workRequestCount = successCount)
    }

    companion object {
        const val NAME: String = "VISITS"
    }
}
