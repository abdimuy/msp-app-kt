package com.example.msp_app.core.sync.pendingwork.di

import android.content.Context
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.GuaranteeEventsWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.GuaranteesWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.LocalSalesWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.PaymentsWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.VisitsWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.gates.InMemorySessionSyncGate
import com.example.msp_app.core.sync.pendingwork.data.observers.RemoteLoggerSessionSyncObserver
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.GuaranteeEventsPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.GuaranteesPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.LocalSalesPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.PaymentsPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.VisitsPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.domain.ports.SessionSyncGate
import com.example.msp_app.core.sync.pendingwork.domain.usecases.SyncAllPendingWorkUseCase
import com.example.msp_app.data.local.datasource.guarantee.GuaranteesLocalDataSource
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource

object PendingWorkSyncFactory {

    private val singletonGate: SessionSyncGate = InMemorySessionSyncGate()

    fun createUseCase(
        context: Context,
        gate: SessionSyncGate = singletonGate
    ): SyncAllPendingWorkUseCase {
        val appContext = context.applicationContext

        val localSalesDataSource = LocalSaleDataSource(appContext)
        val paymentsDataSource = PaymentsLocalDataSource(appContext)
        val visitsDataSource = VisitsLocalDataSource(appContext)
        val guaranteesDataSource = GuaranteesLocalDataSource(appContext)

        val localSalesSynchronizer = LocalSalesPendingSynchronizer(
            fetchPending = { localSalesDataSource.getPendingSales() },
            enqueuer = LocalSalesWorkManagerEnqueuer(appContext)
        )
        val paymentsSynchronizer = PaymentsPendingSynchronizer(
            fetchPending = { paymentsDataSource.getPendingPayments() },
            enqueuer = PaymentsWorkManagerEnqueuer(appContext)
        )
        val visitsSynchronizer = VisitsPendingSynchronizer(
            fetchPending = { visitsDataSource.getPendingVisits() },
            enqueuer = VisitsWorkManagerEnqueuer(appContext)
        )
        val guaranteesSynchronizer = GuaranteesPendingSynchronizer(
            fetchPending = { guaranteesDataSource.getPendingGuarantees() },
            enqueuer = GuaranteesWorkManagerEnqueuer(appContext)
        )
        val guaranteeEventsSynchronizer = GuaranteeEventsPendingSynchronizer(
            fetchPending = { guaranteesDataSource.getPendingGuaranteeEvents() },
            enqueuer = GuaranteeEventsWorkManagerEnqueuer(appContext)
        )

        val observer = RemoteLoggerSessionSyncObserver(appContext)

        return SyncAllPendingWorkUseCase(
            synchronizers = listOf(
                localSalesSynchronizer,
                paymentsSynchronizer,
                visitsSynchronizer,
                guaranteesSynchronizer,
                guaranteeEventsSynchronizer
            ),
            gate = gate,
            observer = observer
        )
    }
}
