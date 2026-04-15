package com.example.msp_app.core.sync.pendingwork.domain.ports

interface LocalSalesWorkEnqueuer {
    fun enqueue(localSaleId: String, userEmail: String, replace: Boolean)
}
