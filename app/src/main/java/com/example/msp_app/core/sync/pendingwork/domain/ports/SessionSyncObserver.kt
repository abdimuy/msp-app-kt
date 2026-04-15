package com.example.msp_app.core.sync.pendingwork.domain.ports

import com.example.msp_app.core.sync.pendingwork.domain.models.SyncResult

interface SessionSyncObserver {
    fun onResult(synchronizerName: String, result: SyncResult)
}
