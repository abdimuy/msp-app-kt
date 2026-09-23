package com.example.msp_app.core.common.sync.pendingwork.fakes

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry

/**
 * Hand-written fakes for the visitas reconciler: public state plus a public
 * list recording every call, per the repo's fakes-only rule. No MockK, no
 * Mockito, ever.
 */

/**
 * In-memory stand-in for the `Visit` table. [synced] is the local
 * `GUARDADO_EN_MICROSIP = 1` set, so "is this visita still pending?" is
 * answerable by a test exactly the way the collector experiences it.
 */
class FakePendingVisitsStore(
    pending: List<String> = emptyList(),
    private val failOnRead: Throwable? = null,
    private val failOnMark: Throwable? = null,
    /** Ids que el marcado local NO puede flipear: retienen un comprobante sin subir. */
    private val retenidas: Set<String> = emptySet()
) : PendingVisitsStore {

    /** What `pendingVisitIds()` will answer. Mutable so a test can script drift. */
    var pending: List<String> = pending

    /** Ids flipped to synced, in the order the use case flipped them. */
    val synced: MutableList<String> = mutableListOf()

    /** One entry per `markSynced` call, holding the exact batch it received. */
    val markSyncedCalls: MutableList<List<String>> = mutableListOf()

    var readCallCount: Int = 0
        private set

    override suspend fun pendingVisitIds(): List<String> {
        readCallCount++
        failOnRead?.let { throw it }
        return pending
    }

    /**
     * Devuelve CUÁNTAS marcó de verdad, igual que el DAO real: por defecto
     * todas, y las de [retenidas] no — que es lo que hace la constraint del
     * comprobante sin entregar (Task 23 / Ruling AR).
     *
     * Un fake que devolviera siempre `visitIds.size` volvería inerte cualquier
     * aserción sobre `confirmedCount`, que es exactamente el defecto que este
     * cambio vino a cerrar.
     */
    override suspend fun markSynced(visitIds: List<String>): Int {
        markSyncedCalls += visitIds
        failOnMark?.let { throw it }
        val marcadas = visitIds.filterNot { it in retenidas }
        synced += marcadas
        return marcadas.size
    }
}

/**
 * Stand-in for `GET /v2/visitas/by-ids`.
 *
 * [requests] records the exact id list of every call, which is what lets a test
 * assert the chunk size is 100 — not merely that "some chunking happened".
 */
class FakeVisitCustodyRegistry(
    /** Ids the server holds. Anything else asked about is simply absent from the answer. */
    private val known: Set<String> = emptySet(),
    /** When set, the Nth call (0-based) throws instead of answering. */
    private val failOnCallIndex: Int? = null,
    private val failure: Throwable = java.io.IOException("sin red"),
    /** Extra ids to volunteer that were never asked for, to exercise the foreign-id gate. */
    private val volunteeredIds: List<String> = emptyList()
) : VisitCustodyRegistry {

    val requests: MutableList<List<String>> = mutableListOf()

    override suspend fun findExisting(visitIds: List<String>): List<String> {
        val index = requests.size
        requests += visitIds
        if (index == failOnCallIndex) throw failure
        return visitIds.filter { it in known } + volunteeredIds
    }
}

/** One recorded `SyncErrorReporter.report` call. */
data class ReportedError(
    val code: String,
    val message: String,
    val props: Map<String, String>
)

/**
 * Records what the domain reported. In production the single implementation
 * forwards straight to the `Telemetry` port; `TelemetrySyncErrorReporterTest`
 * in `:app` proves that hand-off with `RecordingTelemetry`.
 */
class RecordingSyncErrorReporter : SyncErrorReporter {

    val reported: MutableList<ReportedError> = mutableListOf()

    val codes: List<String> get() = reported.map { it.code }

    override fun report(code: String, message: String, props: Map<String, String>) {
        reported += ReportedError(code = code, message = message, props = props)
    }
}
