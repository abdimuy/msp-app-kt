package com.example.msp_app.core.sync.pendingwork.data.enqueuers

import android.content.Context
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.LocalSalesWorkEnqueuer
import com.example.msp_app.workmanager.enqueuePendingLocalSalesWorker

class LocalSalesWorkManagerEnqueuer(
    private val context: Context
) : LocalSalesWorkEnqueuer {
    override fun enqueue(localSaleId: String, userEmail: String, replace: Boolean) {
        enqueuePendingLocalSalesWorker(context, localSaleId, userEmail, replace)
    }
}
