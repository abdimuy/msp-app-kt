package com.example.msp_app.core.sync.pendingwork.di

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileVisitsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridge into the Hilt graph for callers that are not themselves Hilt-injected
 * — WorkManager Workers in this app are plain `(Context, WorkerParameters)`
 * constructors by convention (see `HiltWorkerFactoryFallbackTest`), and a
 * Compose `LaunchedEffect` collecting [com.example.msp_app.core.network
 * .ConnectivityMonitor] outside the composition's own DI scope needs the same
 * door. `EntryPointAccessors` is this repo's established way to read a
 * `SingletonComponent` binding from either — see [com.example.msp_app.core
 * .sync.cobranza.CobranzaSyncProvider.TelemetryEntryPoint] for the precedent.
 *
 * Both accessors resolve **whatever `VisitsReconcileModule` currently binds**,
 * not a cached value — Dagger re-invokes the underlying `@Provides`/`@Binds`
 * on every call to a method here, same as it would for a fresh `@Inject`
 * field. That matters most for [reconcileVisitsUseCase]: it is deliberately
 * NOT `@Singleton` (see `VisitsReconcileModule`'s KDoc) so every resolution
 * rebuilds `V2VisitCustodyRegistry` — and with it the baseURL the kill-switch
 * needs to reach. Callers must call this on every actual trigger, never cache
 * the returned [ReconcileVisitsUseCase] themselves.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VisitsReconcileEntryPoint {
    fun reconcileVisitsUseCase(): ReconcileVisitsUseCase
    fun syncErrorReporter(): SyncErrorReporter
}
