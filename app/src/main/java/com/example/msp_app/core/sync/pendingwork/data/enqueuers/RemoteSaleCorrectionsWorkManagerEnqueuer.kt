package com.example.msp_app.core.sync.pendingwork.data.enqueuers

import android.content.Context
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.RemoteSaleCorrectionsWorkEnqueuer
import com.example.msp_app.workmanager.enqueueRemoteSaleCorrectionWorker

class RemoteSaleCorrectionsWorkManagerEnqueuer(
    private val context: Context
) : RemoteSaleCorrectionsWorkEnqueuer {
    override fun enqueue(localSaleId: String, userEmail: String) {
        enqueueRemoteSaleCorrectionWorker(context, localSaleId, userEmail)
    }
}
