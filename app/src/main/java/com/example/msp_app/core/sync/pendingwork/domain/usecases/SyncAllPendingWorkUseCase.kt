package com.example.msp_app.core.sync.pendingwork.domain.usecases

import com.example.msp_app.core.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.sync.pendingwork.domain.ports.PendingWorkSynchronizer
import com.example.msp_app.core.sync.pendingwork.domain.ports.SessionSyncGate
import com.example.msp_app.core.sync.pendingwork.domain.ports.SessionSyncObserver
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
 *  - The existing manual "Enviar Pendientes" buttons still enqueue with
 *    `ExistingWorkPolicy.KEEP`. Session-sync uses `REPLACE` to force re-runs
 *    of workers stuck in terminal states — the key reason this feature
 *    exists. Keeping the buttons on KEEP avoids fighting with in-flight
 *    user-triggered syncs.
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
