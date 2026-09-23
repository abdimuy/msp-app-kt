package com.example.msp_app.core.sync.pendingwork.di

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SessionSyncGate
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.SyncAllPendingWorkUseCase
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.GuaranteeEventsWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.GuaranteesWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.LocalSalesWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.PaymentsWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.RemoteSaleCorrectionsWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.VisitsWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.gates.InMemorySessionSyncGate
import com.example.msp_app.core.sync.pendingwork.data.observers.RemoteLoggerSessionSyncObserver
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.GuaranteeEventsPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.GuaranteesPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.LocalSalesPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.PaymentsPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.RemoteSaleCorrectionsPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.VisitsPendingSynchronizer
import com.example.msp_app.data.local.datasource.guarantee.GuaranteesLocalDataSource
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource

object PendingWorkSyncFactory {

    private val singletonGate: SessionSyncGate = InMemorySessionSyncGate()

    /**
     * Qué ventas ve el barrido. `getUploadableSales`, NO `getPendingSales`:
     * el barrido reencola en CADA apertura de sesión, así que con la lista
     * cruda de no-enviadas metería a la cola la venta que el dueño está
     * corrigiendo en ese momento — un trabajo que sólo puede chocar contra el
     * fence del candado, quemando un reintento y peleándose con el subidor.
     * Una venta reclamada vuelve a la lista sola en cuanto su arrendamiento
     * vence.
     *
     * (Integración 2026-09-21: este KDoc decía "reencola con `replace = true`".
     * Ya no: el encolado del camino del dinero es `KEEP` y nada más — ver
     * `WorkEnqueuePolicyGuardTest`. La decisión de usar `getUploadableSales`
     * no dependía de la política, y la razón corregida es la de arriba.)
     *
     * Es una función con nombre, y no la lambda pegada abajo, para que la
     * prueba del barrido ejerza ESTA decisión y no una copia suya: si alguien
     * la devuelve a `getPendingSales`, la prueba se pone roja.
     */
    @VisibleForTesting
    internal suspend fun ventasParaElBarrido(
        localSalesDataSource: LocalSaleDataSource,
        clock: AppClock
    ): List<LocalSaleEntity> = localSalesDataSource.getUploadableSales(clock.now().toEpochMilli())

    /**
     * [clock] alimenta el predicado de expiración del candado de
     * `local_sale` (Task 4 del plan "Corregir una venta antes de que suba").
     * Se inyecta —en vez de leer el reloj dentro del barrido— porque el
     * arrendamiento es una regla de negocio con prueba propia, y con el reloj
     * escondido no habría forma de probar "con candado vivo no se reencola,
     * con candado vencido sí" sin esperar tres minutos de verdad.
     */
    fun createUseCase(
        context: Context,
        gate: SessionSyncGate = singletonGate,
        clock: AppClock = AppClock.System
    ): SyncAllPendingWorkUseCase {
        val appContext = context.applicationContext

        val localSalesDataSource = LocalSaleDataSource(appContext)
        val paymentsDataSource = PaymentsLocalDataSource(appContext)
        val visitsDataSource = VisitsLocalDataSource(appContext)
        val guaranteesDataSource = GuaranteesLocalDataSource(appContext)

        val localSalesSynchronizer = LocalSalesPendingSynchronizer(
            fetchPending = { ventasParaElBarrido(localSalesDataSource, clock) },
            enqueuer = LocalSalesWorkManagerEnqueuer(appContext)
        )
        // La SEGUNDA cola de ventas (nivel 2): las correcciones hechas sobre
        // ventas que YA subieron. Es otra lista y otro worker, no un caso del
        // anterior — `getVentasConCorreccionRemotaPendiente()` y
        // `getUploadableSales()` son conjuntos disjuntos por construcción
        // (`ENVIADO = 1` contra `ENVIADO = 0`). Lee del DAO y no de
        // `LocalSaleDataSource` porque esa fachada legacy no expone los
        // métodos del nivel 2.
        val remoteSaleCorrectionsSynchronizer = RemoteSaleCorrectionsPendingSynchronizer(
            fetchPending = {
                AppDatabase.getInstance(appContext).localSaleDao()
                    .getVentasConCorreccionRemotaPendiente()
            },
            enqueuer = RemoteSaleCorrectionsWorkManagerEnqueuer(appContext)
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
                remoteSaleCorrectionsSynchronizer,
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
