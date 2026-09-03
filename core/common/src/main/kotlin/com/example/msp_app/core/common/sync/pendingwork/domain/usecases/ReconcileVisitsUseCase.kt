package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.models.VisitReconcileResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import kotlinx.coroutines.CancellationException

/**
 * Reconciles the phone's pending visitas against the server's custody.
 *
 * ## Why this exists
 *
 * Pagos has a reconciler (digest-first, `by-ids` as the rescue path); visitas
 * had none. That gap is the reason a visita whose upload failed stays invisible
 * until somebody presses the manual "enviar pendientes" button — and a pending
 * visita also blocks "INICIALIZAR SEMANA". This use case closes it.
 *
 * ## Why it is simpler than the pagos one
 *
 * The visitas API has **no digest**, so there is no cheap steady-state
 * pre-check to skip the id round-trip; and the phone never *downloads*
 * visitas, so there is no phantom-deletion or missing-row merge either. What
 * is left is one sentence: *take the locally-pending ids, ask `by-ids` which
 * of them the server already holds, mark those synced.*
 *
 * `CobranzaReconciler` was audited before writing this (475 lines, in `:app`,
 * not hexagonal: it depends directly on `V2CobranzaApi`, concrete DAOs and
 * `ConnectivityMonitor`). Nothing of it is inheritable — there is no
 * `Reconciler` abstraction to implement — and a shared base extracted for one
 * real implementation is exactly the abstraction this repo's YAGNI rule
 * forbids. What was reused is the *shape*: chunk, ask, apply, and never let one
 * failing chunk decide anything about the others.
 *
 * ## The safety rule, stated once
 *
 * A visita is synced when `GUARDADO_EN_MICROSIP = 1`, or when `by-ids`
 * confirms the server holds its UUID. **Never because an HTTP status said so**
 * — see [PendingVisitsStore] for why 409 in particular is not evidence.
 *
 * Concretely, an id reaches [PendingVisitsStore.markSynced] only if the server
 * named it in the answer to a chunk that id was actually in. A throw is
 * "unknown", never "absent": an unreachable server marks nothing, and a chunk
 * that fails halfway leaves its ids pending while the chunks that already
 * succeeded keep their marks (idempotent — the next run re-asks about what is
 * left).
 *
 * ## Enqueue policy
 *
 * This use case enqueues nothing, so it introduces no way to ask WorkManager
 * for `REPLACE`. Draining the still-pending ids stays `VisitsPendingSynchronizer`'s
 * job, which goes through the `VisitsWorkEnqueuer` port that hardcodes
 * `ExistingWorkPolicy.KEEP`.
 *
 * ## Where the batch cap lives
 *
 * [VisitCustodyRegistry.MAX_IDS_PER_REQUEST] — on the port, because it
 * describes the endpoint, not this caller. This use case is one consumer of
 * that number; the Retrofit adapter is another, and it enforces the same
 * bound with a `require` so a future caller that skips the chunking gets a
 * loud local failure instead of a `422`.
 *
 * ## Threading
 *
 * No dispatcher is injected and none is needed: both ports are `suspend` and
 * their production adapters (Room DAO, Retrofit service) already move off the
 * calling thread. Adding a `withContext` here would be a second, redundant hop.
 */
class ReconcileVisitsUseCase(
    private val store: PendingVisitsStore,
    private val registry: VisitCustodyRegistry,
    private val errorReporter: SyncErrorReporter
) {

    suspend fun execute(): VisitReconcileResult {
        val pending = attempt(
            code = ERROR_CODE_PENDIENTES_ILEGIBLES,
            context = CONTEXT_LEER_PENDIENTES
        ) { store.pendingVisitIds() }?.distinct() ?: return VisitReconcileResult.PendingUnreadable

        if (pending.isEmpty()) return VisitReconcileResult.NothingPending

        var requestCount = 0
        var confirmedCount = 0
        var failedRequestCount = 0

        pending.chunked(VisitCustodyRegistry.MAX_IDS_PER_REQUEST).forEach { chunk ->
            requestCount++
            val answered = askServer(chunk)
            if (answered == null) {
                failedRequestCount++
                return@forEach
            }
            confirmedCount += markConfirmed(confirmedFrom(chunk, answered))
        }

        return VisitReconcileResult.Reconciled(
            pendingCount = pending.size,
            requestCount = requestCount,
            confirmedCount = confirmedCount,
            failedRequestCount = failedRequestCount
        )
    }

    /** `null` means "the server did not answer" — never "it holds none of them". */
    private suspend fun askServer(chunk: List<String>): List<String>? = attempt(
        code = ERROR_CODE_CONSULTA_FALLIDA,
        context = CONTEXT_CONSULTA_BY_IDS,
        props = mapOf(PROP_TAMANO_LOTE to chunk.size.toString())
    ) { registry.findExisting(chunk) }

    /**
     * Keeps only the ids that were actually asked about in this chunk.
     *
     * An id the server volunteered but we never asked about cannot be trusted
     * to be one of ours, so it is dropped and reported rather than marked. This
     * is the last gate before a write.
     */
    private fun confirmedFrom(chunk: List<String>, answered: List<String>): List<String> {
        val asked = chunk.toSet()
        val foreign = answered.filterNot { it in asked }
        if (foreign.isNotEmpty()) {
            errorReporter.report(
                code = ERROR_CODE_RESPUESTA_AJENA,
                message = "$CONTEXT_CONSULTA_BY_IDS: la respuesta trae ids que no se preguntaron",
                props = mapOf(
                    PROP_TAMANO_LOTE to chunk.size.toString(),
                    PROP_DESCARTADOS to foreign.size.toString()
                )
            )
        }
        return answered.filter { it in asked }.distinct()
    }

    /** Returns how many ids were really flipped; 0 if the write failed or there was nothing to write. */
    private suspend fun markConfirmed(confirmed: List<String>): Int {
        if (confirmed.isEmpty()) return 0
        val marked = attempt(
            code = ERROR_CODE_MARCADO_FALLIDO,
            context = CONTEXT_MARCAR_SINCRONIZADAS,
            props = mapOf(PROP_TAMANO_LOTE to confirmed.size.toString())
        ) { store.markSynced(confirmed) }
        return if (marked == null) 0 else confirmed.size
    }

    /**
     * Runs [block], reporting any failure through [SyncErrorReporter] and
     * returning `null`. No path swallows a `Throwable` in silence — that is
     * the whole point of the error norm, and of the `IN (...)` incident that
     * produced it.
     *
     * `CancellationException` is rethrown untouched so a cancelled scope really
     * cancels; only genuine failures become a report. The message carries the
     * exception's class name and a static context string, never `e.message`,
     * which could drag business data into telemetry.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // deliberate: the norm requires EVERY throwable to be reported.
    private suspend fun <T> attempt(
        code: String,
        context: String,
        props: Map<String, String> = emptyMap(),
        block: suspend () -> T
    ): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        errorReporter.report(
            code = code,
            message = "$context: ${throwable::class.simpleName}",
            props = props
        )
        null
    }

    companion object {
        /** The local pending list could not be read; the run stopped before any request. */
        const val ERROR_CODE_PENDIENTES_ILEGIBLES: String = "visitas_reconcile_pendientes_ilegibles"

        /** A `by-ids` request threw; its ids stay pending (unknown, not absent). */
        const val ERROR_CODE_CONSULTA_FALLIDA: String = "visitas_reconcile_consulta_fallida"

        /** The server named ids that were not in the chunk; they were dropped, not marked. */
        const val ERROR_CODE_RESPUESTA_AJENA: String = "visitas_reconcile_respuesta_ajena"

        /** The local flip of confirmed ids failed; they stay pending and are re-asked next run. */
        const val ERROR_CODE_MARCADO_FALLIDO: String = "visitas_reconcile_marcado_fallido"

        private const val CONTEXT_LEER_PENDIENTES = "ReconcileVisitsUseCase.pendingVisitIds"
        private const val CONTEXT_CONSULTA_BY_IDS = "ReconcileVisitsUseCase.findExisting"
        private const val CONTEXT_MARCAR_SINCRONIZADAS = "ReconcileVisitsUseCase.markSynced"

        private const val PROP_TAMANO_LOTE = "tamano_lote"
        private const val PROP_DESCARTADOS = "descartados"
    }
}
