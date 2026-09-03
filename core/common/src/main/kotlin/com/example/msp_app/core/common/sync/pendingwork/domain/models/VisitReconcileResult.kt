package com.example.msp_app.core.common.sync.pendingwork.domain.models

/**
 * Outcome of one run of
 * [com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileVisitsUseCase].
 *
 * Every case is reachable without any visita being marked synced; there is no
 * case that implies a mark. Marking is reported as a count, never inferred
 * from the shape of the result.
 */
sealed class VisitReconcileResult {

    /** Nothing was pending locally. **No request was made** — not a request with an empty list. */
    object NothingPending : VisitReconcileResult()

    /**
     * The local pending list could not be read, so the run stopped before any
     * request. Nothing was marked. Reported through `SyncErrorReporter`.
     */
    object PendingUnreadable : VisitReconcileResult()

    /**
     * The run completed. It may still have failed in part: [failedRequestCount]
     * counts the chunks whose `by-ids` call threw, and those ids stay pending
     * on purpose — a chunk that fails is "unknown", never "the server does not
     * have them".
     *
     * @param pendingCount distinct ids that were pending when the run started.
     * @param requestCount `by-ids` requests attempted (one per chunk).
     * @param confirmedCount ids the server named AND that were successfully
     *   flipped to synced locally.
     * @param failedRequestCount requests that threw; their ids remain pending.
     */
    data class Reconciled(
        val pendingCount: Int,
        val requestCount: Int,
        val confirmedCount: Int,
        val failedRequestCount: Int
    ) : VisitReconcileResult() {

        /** Ids still pending after this run — the ones the next run retries. */
        val stillPendingCount: Int get() = pendingCount - confirmedCount
    }
}
