package com.example.msp_app.core.common.sync.pendingwork.domain.ports

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult

interface SessionSyncObserver {
    fun onResult(synchronizerName: String, result: SyncResult)
}
