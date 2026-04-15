package com.example.msp_app.core.sync.pendingwork.data.gates

import com.example.msp_app.core.sync.pendingwork.domain.ports.SessionSyncGate
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-local gate: the first caller to see a given [userId] wins; all
 * subsequent callers with the same id lose. A different id resets — useful
 * for logout + login of another user in the same process.
 *
 * This instance is a singleton at the composition root, so across the whole
 * process there is one "last synced user id" slot. Process death resets it.
 */
class InMemorySessionSyncGate : SessionSyncGate {

    private val lastSyncedUserId = AtomicReference<String?>(null)

    override fun markIfNotSynced(userId: String): Boolean {
        while (true) {
            val current = lastSyncedUserId.get()
            if (current == userId) return false
            if (lastSyncedUserId.compareAndSet(current, userId)) return true
        }
    }
}
