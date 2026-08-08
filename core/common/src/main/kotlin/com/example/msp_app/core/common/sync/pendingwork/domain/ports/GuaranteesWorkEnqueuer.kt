package com.example.msp_app.core.common.sync.pendingwork.domain.ports

interface GuaranteesWorkEnqueuer {
    fun enqueue(guaranteeExternalId: String, replace: Boolean)
}
