package com.example.msp_app.core.sync.pendingwork.data.visits

import android.content.Context
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.TriggerVisitsReconciliationUseCase
import com.example.msp_app.core.sync.pendingwork.di.VisitsReconcileEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Process-wide singleton factory for [TriggerVisitsReconciliationUseCase] —
 * same shape as [com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider]
 * and [com.example.msp_app.core.sync.cobranza.CobranzaReconcilerProvider]: a
 * plain `object` rather than a Hilt `@Singleton` binding.
 *
 * That choice is deliberate, not incidental. Task 11's three triggers (app
 * open, periodic `WorkManager` worker, connectivity-restored) each run
 * outside Hilt's own injection (see [VisitsReconcileEntryPoint]'s KDoc), so
 * there is no Hilt-injected consumer for a `@Singleton` binding to serve in
 * the first place — and making this a Hilt module would put a NEW module in
 * front of `NetworkKillSwitchGuardTest`'s `modulosDeRed` allowlist for no
 * reason: this object holds no `@Provides` at all, so the guard's sweep
 * (which only inspects `@Provides` methods) has nothing here to find.
 *
 * The mutex inside [TriggerVisitsReconciliationUseCase] only means anything
 * if every trigger shares ONE instance — that is the entire reason this is a
 * singleton `object` rather than a `build()` called fresh per trigger.
 */
object VisitsReconcileTriggerProvider {

    @Volatile
    private var instance: TriggerVisitsReconciliationUseCase? = null

    fun get(context: Context): TriggerVisitsReconciliationUseCase {
        return instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }
    }

    /**
     * Both lambdas below re-resolve [VisitsReconcileEntryPoint] on every
     * actual call — `entryPoint` is `by lazy` only so the FIRST resolution is
     * deferred to first use rather than paid at `build()` time, not so it
     * gets cached across calls (`EntryPointAccessors.fromApplication` itself
     * is cheap; it is [VisitsReconcileEntryPoint.reconcileVisitsUseCase] that
     * must run fresh every time — see that interface's KDoc).
     */
    private fun build(context: Context): TriggerVisitsReconciliationUseCase {
        val appContext = context.applicationContext
        val entryPoint by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            EntryPointAccessors.fromApplication(appContext, VisitsReconcileEntryPoint::class.java)
        }
        return TriggerVisitsReconciliationUseCase(
            reconcileVisits = { entryPoint.reconcileVisitsUseCase().execute() },
            errorReporter = object : SyncErrorReporter {
                override fun report(code: String, message: String, props: Map<String, String>) {
                    entryPoint.syncErrorReporter().report(code, message, props)
                }
            }
        )
    }
}
