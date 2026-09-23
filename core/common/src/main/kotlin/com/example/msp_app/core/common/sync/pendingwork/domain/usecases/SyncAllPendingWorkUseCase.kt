package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingWorkSynchronizer
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SessionSyncGate
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SessionSyncObserver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orchestrates the 5 pending-work synchronizers when a user session starts.
 *
 * Design notes (see PR description for the full rationale):
 *
 *  - Uses [SessionSyncGate] for process-level idempotency. `LaunchedEffect`
 *    may re-trigger on Firestore snapshot emissions; the gate keeps us honest.
 *
 *  - Synchronizers run in parallel inside a [coroutineScope] so that total
 *    wall time ≈ max(individual times) rather than the sum. Each call is
 *    wrapped in `runCatching` so one failing synchronizer never tumbles the
 *    others — callers always get a result for every synchronizer.
 *
 *  - A global [withTimeoutOrNull] of 60s caps the whole operation. Timeout
 *    returns an empty map (the caller treats this as "nothing happened this
 *    session; next session retries").
 *
 *  - The observer is fire-and-forget: flakiness in a logging adapter must not
 *    take down the sync.
 *
 * Known interactions (not fixed here):
 *
 *  - `SalesViewModel.syncSales` (app/src/main/.../SalesViewModel.kt:122-147)
 *    refuses to run when payments/visits/guarantees/events backlog exists.
 *    Session-sync does not change that precondition, but makes the backlog
 *    start draining silently right when the user opens the app. A future
 *    fix would expose a `sessionSyncInProgress` flag for `syncSales` to wait
 *    on.
 *
 *  - **Enqueue policy: `ExistingWorkPolicy.KEEP`, always — and it is no longer
 *    a runtime choice.** Audited against WorkManager's real contract
 *    (2026-09-01, Task 6; closed 2026-09-01, fix-round 1): `KEEP` only skips
 *    a fresh enqueue while the existing unique-name work is still
 *    ENQUEUED/RUNNING/BLOCKED — once it reaches a terminal state
 *    (SUCCEEDED/FAILED/CANCELLED), `KEEP` enqueues the new request exactly
 *    like `REPLACE` would. So `REPLACE`'s only real effect is cancelling a
 *    worker that is *currently uploading* and starting a fresh attempt in
 *    its place. For payments/visits/guarantees/guarantee-events that means a
 *    live POST gets cut off client-side while the server may already have
 *    accepted it, and the replacement worker re-sends the same item —
 *    duplicate work on the money path, for zero benefit (`KEEP` already
 *    re-enqueues terminal work for free). This use case can be triggered
 *    more than once per session (the gate above exists because
 *    `LaunchedEffect` can re-fire on Firestore snapshot emissions), and
 *    every synchronizer's unique work name is scoped per item
 *    (`sync_pending_visit_$id`, `sync_pending_payments_$id`, etc., plus one
 *    shared name for the guarantee-events batch) — so overlap is exactly the
 *    case `REPLACE` would mishandle.
 *
 *    Fix-round 1 found this use case's own synchronizers were not the only
 *    path into the same unique work names: the legacy manual "Enviar
 *    Pendientes" button (`HomeFooterSection` → `VisitsViewModel
 *    .syncPendingVisits` / `PaymentsViewModel.syncPendingPayments` /
 *    `GuaranteesViewModel.syncPendingGuarantees` / `syncPendingGuaranteeEvents`)
 *    and `NewLocalSaleViewModel.retryPendingSales` delegate into the exact
 *    same `enqueuePendingXWorker` functions in `WorkManagerUtils.kt`, which
 *    build the exact same unique names — so a collector pressing that button
 *    mid-session-sync could cancel a live upload this use case had already
 *    started. Rather than rely on every future caller remembering to pass
 *    `replace = false`, the `replace` parameter was removed from all five
 *    `enqueuePendingXWorker` functions (and from the five domain ports —
 *    `VisitsWorkEnqueuer`, `PaymentsWorkEnqueuer`, `GuaranteesWorkEnqueuer`,
 *    `GuaranteeEventsWorkEnqueuer`, `LocalSalesWorkEnqueuer`) entirely; they
 *    hardcode `ExistingWorkPolicy.KEEP` internally. `REPLACE` is no longer
 *    expressible anywhere in this call chain — not by this use case, not by
 *    the button, not by Task 11's future triggers. The compiler is the
 *    enforcement now, not this comment.
 *
 *  - Cap of 50 items per synchronizer is a safety valve for "clear data and
 *    reinstall" scenarios; overflow rolls to the next session.
 *
 *  - Redundancy with `Home.kt:197-203` `LaunchedEffect` (calls
 *    syncPendingGuarantees/Events). Kept for now; will be removed in a
 *    follow-up once session-sync is proven in production.
 */
class SyncAllPendingWorkUseCase(
    private val synchronizers: List<PendingWorkSynchronizer>,
    private val gate: SessionSyncGate,
    private val observer: SessionSyncObserver,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) {

    suspend fun execute(context: SyncContext): Map<String, SyncResult> {
        if (!gate.markIfNotSynced(context.userId)) {
            return emptyMap()
        }

        val results: Map<String, SyncResult> = withTimeoutOrNull(timeoutMillis) {
            coroutineScope {
                synchronizers.map { synchronizer ->
                    async {
                        val result = runCatching { synchronizer.sync(context) }
                            .getOrElse { SyncResult.Failed(it) }
                        synchronizer.name to result
                    }
                }.awaitAll().toMap()
            }
        } ?: emptyMap()

        results.forEach { (name, result) ->
            runCatching { observer.onResult(name, result) }
        }

        return results
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 60_000L
        const val MAX_ITEMS_PER_SYNC: Int = 50
    }
}
