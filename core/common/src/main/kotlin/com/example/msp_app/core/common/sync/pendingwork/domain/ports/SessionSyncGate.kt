package com.example.msp_app.core.common.sync.pendingwork.domain.ports

interface SessionSyncGate {
    /**
     * Returns `true` exactly once per `userId` per process lifetime. Subsequent
     * calls with the same `userId` return `false`. A different `userId` resets.
     */
    fun markIfNotSynced(userId: String): Boolean
}
