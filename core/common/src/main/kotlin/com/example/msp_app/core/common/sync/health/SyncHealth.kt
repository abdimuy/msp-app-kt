package com.example.msp_app.core.common.sync.health

/**
 * Pure snapshot of the outbox's sync state, counting items across the 5
 * pending-work types (payments, guarantees, guarantee events, local sales,
 * visits). This is the base of the future "sync pill" (spec §13 #2): the
 * Compose UI (Plan 3) renders [status]/[hasBacklog], it never computes them.
 *
 * @property pending items enqueued but not yet acked by their synchronizer.
 * @property confirmed items already acked (uploaded / applied server-side).
 */
data class SyncHealth(val pending: Int, val confirmed: Int) {

    init {
        require(pending >= 0) { "pending no puede ser negativo: $pending" }
        require(confirmed >= 0) { "confirmed no puede ser negativo: $confirmed" }
    }

    val total: Int
        get() = pending + confirmed

    val hasBacklog: Boolean
        get() = pending > 0

    val status: SyncStatus
        get() = if (hasBacklog) SyncStatus.BACKLOG else SyncStatus.HEALTHY
}

/**
 * Coarse status derived from [SyncHealth], intended for the sync pill's color
 * / label. Only two states are derivable from `(pending, confirmed)` alone —
 * a "SYNCING" (actively uploading right now) state would need an in-flight
 * signal this value object does not carry, so it is deliberately not modeled
 * here (YAGNI; add it once a real source can distinguish it).
 */
enum class SyncStatus {
    HEALTHY,
    BACKLOG
}
