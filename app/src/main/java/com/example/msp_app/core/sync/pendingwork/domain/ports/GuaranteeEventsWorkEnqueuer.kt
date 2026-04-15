package com.example.msp_app.core.sync.pendingwork.domain.ports

interface GuaranteeEventsWorkEnqueuer {
    fun enqueue(replace: Boolean)
}
