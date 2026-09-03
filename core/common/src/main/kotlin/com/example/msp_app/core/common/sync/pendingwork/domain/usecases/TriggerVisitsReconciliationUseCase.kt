package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.models.VisitReconcileResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/**
 * The single door every visitas-reconcile trigger must knock on.
 *
 * ## Why this exists
 *
 * [ReconcileVisitsUseCase] has three independent triggers (Task 11): app open,
 * a 15-minute periodic backstop, and a connectivity-restored signal. Nothing
 * stops two of them from firing within the same second — a collector opening
 * the app right as signal comes back is not an edge case, it is the normal
 * shape of a delivery route. Three overlapping triggers each starting their
 * own `by-ids` round trip is wasted radio and battery on a low-end phone for
 * zero extra correctness, since [ReconcileVisitsUseCase] is already
 * idempotent — a second concurrent run would just re-ask about ids the first
 * run is already asking about.
 *
 * ## The mechanism
 *
 * A single [Mutex], held for the lifetime of one call to [execute]. `tryLock`
 * is **non-suspending** and returns `false` immediately when the lock is
 * already held, so a trigger that loses the race never queues behind the
 * winner — it simply skips, returning `null`. There is no debounce window and
 * no cooldown: a trigger that arrives *after* the in-flight run has released
 * the lock runs for real. This is deliberate — periodic must still fire every
 * ~15 minutes even if an app-open run happened a second ago and finished; the
 * guard only ever suppresses genuine overlap, never a legitimately later call.
 *
 * For the guard to do anything, every trigger must call the SAME instance.
 * That is a wiring concern of the caller (`:app` holds one process-wide
 * singleton — see `VisitsReconcileTriggerProvider`), not of this class: a
 * fresh instance per call would make the mutex pointless.
 *
 * ## Accepted trade-off: a skip can mean a bounded delay, not just a duplicate
 *
 * `tryLock` collapsing two SIMULTANEOUS signals into one run is the case
 * above. There is a different case worth naming explicitly: a run can already
 * be in flight, stuck on a slow network call, when a trigger carrying genuine
 * NEW information arrives — e.g. connectivity returns mid-run after a stretch
 * offline. That trigger is skipped too, even though "connectivity just
 * returned" is new, not a duplicate of whatever started the in-flight run.
 *
 * This was decided, not overlooked: the in-flight run cannot hang forever.
 * `RetrofitClientFactory.V2_TIMEOUT_SECONDS = 60` bounds both connect and read
 * for the `by-ids` call `ReconcileVisitsUseCase` makes, so the mutex releases
 * within, worst case, low tens of seconds per chunk — nowhere close to the
 * 15-minute periodic backstop that would otherwise be the next chance to
 * reconcile. The skipped signal is not lost information, only a bounded
 * delay: the connectivity-restored state persists (it is not an edge-only
 * pulse), so the very next trigger — periodic, or another connectivity flicker
 * — picks up the same pending ids once the lock frees. **No case exists where
 * a skip here means the reconciliation never happens**, only that it happens
 * up to ~60s-per-chunk later than the eager path would have.
 *
 * ## Why [reconcileVisits] is a function, not a [ReconcileVisitsUseCase]
 *
 * `VisitsReconcileModule` (`:app`, Task 10) deliberately keeps
 * [ReconcileVisitsUseCase] un-scoped so every resolution rebuilds
 * `V2VisitCustodyRegistry` fresh — freezing it would defeat the baseURL
 * kill-switch. If this coordinator held a [ReconcileVisitsUseCase] directly,
 * making the coordinator itself a process-wide singleton (required for the
 * mutex to mean anything) would freeze exactly that chain right back. Taking
 * a `suspend () -> VisitReconcileResult` lets the caller re-resolve the real
 * use case on every actual invocation while the mutex/singleton lives here.
 *
 * ## Error norm
 *
 * [ReconcileVisitsUseCase.execute] already reports every internal failure and
 * never throws (only `CancellationException` escapes it). The `catch` below
 * is defense in depth for the one throw this class does not control: the
 * caller's [reconcileVisits] lambda resolves the use case from the Hilt graph
 * on every call (see above), and that resolution itself can throw — e.g. a
 * `WorkManager`-driven trigger racing app start before the graph is ready.
 */
class TriggerVisitsReconciliationUseCase(
    private val reconcileVisits: suspend () -> VisitReconcileResult,
    private val errorReporter: SyncErrorReporter
) {
    private val mutex = Mutex()

    /**
     * @return the reconciliation's result, or `null` if another trigger was
     *   already in flight and this one was skipped.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // deliberate: the error norm requires EVERY throwable to be reported.
    suspend fun execute(): VisitReconcileResult? {
        if (!mutex.tryLock()) return null
        return try {
            reconcileVisits()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            errorReporter.report(
                code = ERROR_CODE_DISPARO_FALLIDO,
                message = "$CONTEXT_EXECUTE: ${throwable::class.simpleName}",
                props = emptyMap()
            )
            null
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        /**
         * Resolving or running the reconciliation threw something
         * [ReconcileVisitsUseCase.execute] itself never lets escape — most
         * likely the caller's Hilt lookup, not the reconciliation logic. The
         * run is lost this time; the next trigger (periodic, at worst 15
         * minutes away) retries.
         */
        const val ERROR_CODE_DISPARO_FALLIDO: String = "visitas_reconcile_disparo_fallido"

        private const val CONTEXT_EXECUTE = "TriggerVisitsReconciliationUseCase.execute"
    }
}
