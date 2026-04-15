package com.example.msp_app.core.sync.pendingwork.domain.models

sealed class SyncResult {
    object NothingPending : SyncResult()

    object Skipped : SyncResult()

    data class Enqueued(
        val itemCount: Int,
        val workRequestCount: Int
    ) : SyncResult()

    data class Failed(val cause: Throwable) : SyncResult()
}
