package com.example.msp_app.core.sync.pendingwork.domain.ports

import com.example.msp_app.core.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.sync.pendingwork.domain.models.SyncResult

interface PendingWorkSynchronizer {
    val name: String
    suspend fun sync(context: SyncContext): SyncResult
}
