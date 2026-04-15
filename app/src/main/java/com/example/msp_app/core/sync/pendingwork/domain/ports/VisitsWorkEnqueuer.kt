package com.example.msp_app.core.sync.pendingwork.domain.ports

interface VisitsWorkEnqueuer {
    fun enqueue(visitId: String, replace: Boolean)
}
