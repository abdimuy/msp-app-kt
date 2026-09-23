package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingVisitsStore
import com.example.msp_app.core.database.dao.visit.VisitDao
import javax.inject.Inject

/**
 * Adapter of [PendingVisitsStore] over Room's `Visit` table.
 *
 * Reads reuse [VisitDao.getPendingVisits] (`GUARDADO_EN_MICROSIP = 0`) rather
 * than adding a second, id-only query: the pending set is a handful of rows on
 * a working phone, and one query with one meaning is worth more than the bytes
 * a projection would save.
 *
 * Writes go through [VisitDao.markSyncedByIds], which flips
 * `GUARDADO_EN_MICROSIP` to 1 for exactly the ids given and nothing else.
 */
class RoomPendingVisitsStore @Inject constructor(
    private val visitDao: VisitDao
) : PendingVisitsStore {

    override suspend fun pendingVisitIds(): List<String> = visitDao.getPendingVisits().map { it.ID }

    /**
     * The reconciler already hands over at most 100 ids per call, so this chunk
     * never actually splits today. It stays because the ceiling it guards is
     * real and cheap: every Android below API 31 caps a statement at 999 bound
     * parameters, and `CLAUDE.md` records what an unbounded `IN (...)` cost
     * once — "too many SQL variables", caught by an outer `try/catch` and
     * turned into a silent error on every tick, invisible until it had burned
     * hours. A future caller with a bigger batch should hit a slower path, not
     * that one.
     *
     * The per-chunk counts are **summed and returned**, not dropped: since Task
     * 23 [VisitDao.markSyncedByIds] refuses to mark a visita whose comprobante
     * is still undelivered, so the count is what tells "confirmed" from "held
     * back by a photo".
     */
    override suspend fun markSynced(visitIds: List<String>): Int =
        visitIds.chunked(SQLITE_MAX_IN_PARAMS).sumOf { chunk ->
            visitDao.markSyncedByIds(chunk)
        }

    companion object {
        /** Below SQLITE_MAX_VARIABLE_NUMBER (999) with margin; `minSdk` here is 24. */
        const val SQLITE_MAX_IN_PARAMS: Int = 900
    }
}
