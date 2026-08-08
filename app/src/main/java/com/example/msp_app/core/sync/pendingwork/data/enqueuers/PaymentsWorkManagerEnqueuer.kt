package com.example.msp_app.core.sync.pendingwork.data.enqueuers

import android.content.Context
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PaymentsWorkEnqueuer
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker

class PaymentsWorkManagerEnqueuer(
    private val context: Context
) : PaymentsWorkEnqueuer {
    override fun enqueue(paymentId: String, replace: Boolean) {
        enqueuePendingPaymentsWorker(context, paymentId, replace)
    }
}
