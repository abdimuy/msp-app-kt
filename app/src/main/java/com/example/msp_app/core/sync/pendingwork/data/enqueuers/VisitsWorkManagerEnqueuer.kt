package com.example.msp_app.core.sync.pendingwork.data.enqueuers

import android.content.Context
import com.example.msp_app.core.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.workmanager.enqueuePendingVisitsWorker

class VisitsWorkManagerEnqueuer(
    private val context: Context
) : VisitsWorkEnqueuer {
    override fun enqueue(visitId: String, replace: Boolean) {
        enqueuePendingVisitsWorker(context, visitId, replace)
    }
}
