package com.example.msp_app.core.common.sync.pendingwork.domain.ports

interface GuaranteeEventsWorkEnqueuer {
    fun enqueue(replace: Boolean)
}
