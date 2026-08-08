package com.example.msp_app.core.common.sync.pendingwork.domain.ports

interface LocalSalesWorkEnqueuer {
    fun enqueue(localSaleId: String, userEmail: String, replace: Boolean)
}
