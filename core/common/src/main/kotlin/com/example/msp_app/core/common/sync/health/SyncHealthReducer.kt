package com.example.msp_app.core.common.sync.health

/**
 * Per-type pending/confirmed counts, one instance per pendingwork
 * synchronizer (payments, guarantees, guarantee events, local sales,
 * visits). Whoever counts rows (Room DAO, WorkManager query, ...) produces
 * these; this module only reduces them.
 */
data class SyncTypeCount(val pending: Int, val confirmed: Int)

/**
 * Pure reducer: combines the per-synchronizer [SyncTypeCount]s into one
 * [SyncHealth] snapshot for the sync pill. No I/O, no knowledge of *how* the
 * counts were obtained.
 */
object SyncHealthReducer {
    fun reduce(counts: Iterable<SyncTypeCount>): SyncHealth = SyncHealth(
        pending = counts.sumOf { it.pending },
        confirmed = counts.sumOf { it.confirmed }
    )
}
