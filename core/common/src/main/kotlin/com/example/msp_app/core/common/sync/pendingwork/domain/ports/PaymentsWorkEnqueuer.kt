package com.example.msp_app.core.common.sync.pendingwork.domain.ports

interface PaymentsWorkEnqueuer {
    fun enqueue(paymentId: String, replace: Boolean)
}
