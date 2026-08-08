package com.example.msp_app.core.sync.pendingwork.data.enqueuers

import android.content.Context
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.GuaranteeEventsWorkEnqueuer
import com.example.msp_app.workmanager.enqueuePendingGuaranteeEventsWorker

class GuaranteeEventsWorkManagerEnqueuer(
    private val context: Context
) : GuaranteeEventsWorkEnqueuer {
    override fun enqueue(replace: Boolean) {
        enqueuePendingGuaranteeEventsWorker(context, replace)
    }
}
