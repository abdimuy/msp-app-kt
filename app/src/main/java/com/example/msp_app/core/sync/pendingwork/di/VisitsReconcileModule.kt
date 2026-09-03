package com.example.msp_app.core.sync.pendingwork.di

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileVisitsUseCase
import com.example.msp_app.core.sync.pendingwork.data.visits.RoomPendingVisitsStore
import com.example.msp_app.core.sync.pendingwork.data.visits.TelemetrySyncErrorReporter
import com.example.msp_app.core.sync.pendingwork.data.visits.V2VisitCustodyRegistry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The single file that sees both faces of the visitas reconciler and joins
 * them — the `di/` role of the hexagonal contract.
 *
 * Wired with Hilt on purpose, unlike [PendingWorkSyncFactory], the hand-wired
 * `object` next door. That factory predates the graph and stays as it is;
 * copying its shape into new architecture would mean the compiler validates
 * nothing about these bindings. `VisitsReconcileHiltGraphTest` proves the real
 * `:app` graph resolves [ReconcileVisitsUseCase] end to end.
 *
 * **Nothing here is `@Singleton`.** [V2VisitCustodyRegistry] transitively holds
 * a Retrofit service proxy, and a scoped holder would freeze it for the life of
 * the process — the exact shape `NetworkKillSwitchGuardTest` exists to forbid.
 * The other two are cheap wrappers over an already-scoped DAO and an
 * already-scoped `Telemetry`, so scoping them would buy nothing and would only
 * invite the pattern to spread to the one binding where it is unsafe.
 *
 * Task 11 owns the triggers (app open, periodic, connectivity change) that call
 * the use case; this task builds and wires it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VisitsReconcileModule {

    @Binds
    abstract fun bindPendingVisitsStore(impl: RoomPendingVisitsStore): PendingVisitsStore

    @Binds
    abstract fun bindVisitCustodyRegistry(impl: V2VisitCustodyRegistry): VisitCustodyRegistry

    @Binds
    abstract fun bindSyncErrorReporter(impl: TelemetrySyncErrorReporter): SyncErrorReporter

    companion object {

        @Provides
        fun provideReconcileVisitsUseCase(
            store: PendingVisitsStore,
            registry: VisitCustodyRegistry,
            errorReporter: SyncErrorReporter
        ): ReconcileVisitsUseCase = ReconcileVisitsUseCase(
            store = store,
            registry = registry,
            errorReporter = errorReporter
        )
    }
}
