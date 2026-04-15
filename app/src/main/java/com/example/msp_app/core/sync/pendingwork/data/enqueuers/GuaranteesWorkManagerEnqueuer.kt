package com.example.msp_app.core.sync.pendingwork.data.enqueuers

import android.content.Context
import com.example.msp_app.core.sync.pendingwork.domain.ports.GuaranteesWorkEnqueuer
import com.example.msp_app.workmanager.enqueuePendingGuaranteesWorker

class GuaranteesWorkManagerEnqueuer(
    private val context: Context
) : GuaranteesWorkEnqueuer {
    override fun enqueue(guaranteeExternalId: String, replace: Boolean) {
        enqueuePendingGuaranteesWorker(context, guaranteeExternalId, replace)
    }
}
