package com.example.msp_app.core.sync.pendingwork.data.visits

import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingVisitsStore
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.visitas.envio.FirestoreModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import java.time.Instant
import javax.inject.Inject

/**
 * Adapter of [PendingVisitsStore] over Room's `Visit` table.
 *
 * Reads reuse [VisitDao.getPendingVisits] (`GUARDADO_EN_MICROSIP = 0`) rather
 * than adding a second, id-only query: the pending set is a handful of rows on
 * a working phone, and one query with one meaning is worth more than the bytes
 * a projection would save.
 *
 * Writes flip `GUARDADO_EN_MICROSIP` to 1 for exactly the ids given and
 * nothing else. Which DAO call does it depends on the kill-switch
 * (`config/visitas_envio`, plan visitas P1 Tarea 10):
 *
 *  - **V3** (por omisión): [VisitDao.confirmar], que además estampa
 *    `CONFIRMADA_EN`. `by-ids` nombró el id: es prueba del servidor, y sin la
 *    marca la poda de la Tarea 12 nunca soltaría la fila y la reconciliación
 *    inversa (Tarea 13) la volvería a preguntar cada día como legada.
 *  - **V2_ACTUAL**: [VisitDao.markSyncedByIds], la conducta de antes de P1,
 *    intacta. El kill-switch revierte TODO el envío nuevo, también esto.
 *
 * Las dos conservan la retención por foto (Ruling AR) y devuelven cuántas
 * filas cambiaron de verdad.
 */
class RoomPendingVisitsStore @VisibleForTesting internal constructor(
    private val visitDao: VisitDao,
    private val modo: () -> ModoEnvioVisitas,
    private val ahora: () -> Instant
) : PendingVisitsStore {

    @Inject
    constructor(visitDao: VisitDao) : this(
        visitDao = visitDao,
        modo = { FirestoreModoEnvioVisitas.actual },
        ahora = { Instant.now() }
    )

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
     * back by a photo". [VisitDao.confirmar] keeps that same retention.
     *
     * El modo se lee UNA vez por llamada: una tanda no queda mitad con marca y
     * mitad sin ella si el kill-switch cambia a media escritura.
     */
    override suspend fun markSynced(visitIds: List<String>): Int {
        if (modo() == ModoEnvioVisitas.V2_ACTUAL) {
            return visitIds.chunked(SQLITE_MAX_IN_PARAMS).sumOf { chunk ->
                visitDao.markSyncedByIds(chunk)
            }
        }
        val estampa = AppTime.toWireFormat(ahora())
        return visitIds.chunked(SQLITE_MAX_IN_PARAMS).sumOf { chunk ->
            visitDao.confirmar(chunk, estampa)
        }
    }

    companion object {
        /** Below SQLITE_MAX_VARIABLE_NUMBER (999) with margin; `minSdk` here is 24. */
        const val SQLITE_MAX_IN_PARAMS: Int = 900
    }
}
