package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import kotlinx.coroutines.CancellationException

/**
 * Turns one server push event into (at most) one reconciliation trigger.
 *
 * ## The fourth trigger, and nothing more
 *
 * Task 11 gave [ReconcileVisitsUseCase] three triggers: app open, a 15-minute
 * periodic backstop, and connectivity restored. This is the fourth and the
 * fastest — instead of waiting up to 15 minutes for the periodic tick, the
 * server says "I have a visita" and the phone reconciles now.
 *
 * **The event is a trigger, not an authority.** It marks nothing. It carries no
 * visita ids and could not mark anything even if it wanted to: all it does is
 * call [triggerReconciliation], and the reconciliation then confirms through
 * `by-ids` exactly as it does for the other three triggers. Task 10 established
 * exactly one write path to "synced" — positive confirmation — and two paths
 * that could disagree about money is the thing this plan exists to avoid.
 *
 * Because it funnels into the same process-wide
 * [TriggerVisitsReconciliationUseCase], Task 11's `Mutex.tryLock()` debounces
 * a push against the other three for free.
 *
 * ## Why this class switches on the event name — the expensive lesson
 *
 * The cobranza SSE client already in the field does **not**. Measured, not
 * assumed: a probe drove the real `CobranzaSseSubscriber` against a
 * `MockWebServer` emitting an unrecognised event type and recorded
 * `[(PAGOS, [])]` — the listener reads the event name into a log line
 * (`CobranzaSseSubscriber.kt:324-333`) and then discards it, deciding what an
 * event means from *which socket delivered it*. The connection survived; the
 * client acted anyway, and in production that lands in `manager.syncNow()`.
 *
 * So "an unknown event does not break the stream" is not the property worth
 * having. **Surviving an event you then act on wrongly is the defect.** This
 * class asserts the stronger property: an unrecognised type produces
 * [Outcome.IGNORADO_TIPO_DESCONOCIDO] and no call to [triggerReconciliation]
 * at all.
 *
 * ## Why an unknown type is not reported as an error
 *
 * Forward compatibility working as designed is not a failure. A future server
 * adding an event this build predates is expected, and reporting each one
 * would turn a healthy deploy skew into a telemetry flood at whatever rate
 * that future event fires. Genuine failures — a throw out of the trigger, a
 * dropped stream — do report, each with its own named constant below.
 */
class HandleVisitsPushEventUseCase(
    private val triggerReconciliation: suspend () -> Unit,
    private val errorReporter: SyncErrorReporter
) {

    /** What [onEvent] did with one incoming event. */
    enum class Outcome {
        /** The event was recognised and a reconciliation was triggered. */
        RECONCILIACION_DISPARADA,

        /** The event name is not one this build knows. Nothing happened. */
        IGNORADO_TIPO_DESCONOCIDO,

        /** The trigger threw. Reported; the next trigger retries. */
        FALLO_AL_DISPARAR
    }

    /**
     * @param type the SSE `event:` name, or `null` when the server sent an
     *   event with no name at all. A nameless event is treated exactly like an
     *   unrecognised one — it is not evidence of anything.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // deliberate: the error norm requires EVERY throwable to be reported.
    suspend fun onEvent(type: String?): Outcome {
        if (type != EVENT_VISITAS_CONFIRMADAS) return Outcome.IGNORADO_TIPO_DESCONOCIDO

        return try {
            triggerReconciliation()
            Outcome.RECONCILIACION_DISPARADA
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            errorReporter.report(
                code = ERROR_CODE_EVENTO_FALLIDO,
                message = "$CONTEXT_ON_EVENT: ${throwable::class.simpleName}",
                props = emptyMap()
            )
            Outcome.FALLO_AL_DISPARAR
        }
    }

    /**
     * Reports a stream that dropped or failed to open.
     *
     * A dropped SSE connection that reports nothing is invisible by
     * construction: the phone simply stops receiving pushes and the only
     * symptom is visitas reconciling on the 15-minute backstop instead of in
     * seconds — indistinguishable from a quiet day, from the outside.
     *
     * @param cause the throwable, when there was one. Only its class name is
     *   reported: a raw `message` can drag business data along with it, which
     *   the anti-PII discipline forbids.
     * @param httpCode the response status when the failure carried one.
     */
    fun onStreamFailure(cause: Throwable?, httpCode: Int?) {
        errorReporter.report(
            code = ERROR_CODE_STREAM_CAIDO,
            message = "$CONTEXT_STREAM: ${cause?.let { it::class.simpleName } ?: NO_THROWABLE}",
            props = httpCode?.let { mapOf(PROP_HTTP_CODE to it.toString()) } ?: emptyMap()
        )
    }

    /**
     * Reports that the server answered 503 — the visitas SSE killswitch is off
     * at the server and the phone will stop trying.
     *
     * Deliberately a *distinct* code from [ERROR_CODE_STREAM_CAIDO]. Both stop
     * the push channel, but one is an outage to chase and the other is an ops
     * decision someone made on purpose; a single code would make the two
     * indistinguishable in exactly the moment somebody is trying to tell them
     * apart. The other three triggers keep working either way.
     */
    fun onStreamDisabledByServer() {
        errorReporter.report(
            code = ERROR_CODE_STREAM_DESHABILITADO,
            message = CONTEXT_STREAM_DISABLED,
            props = emptyMap()
        )
    }

    /**
     * Reports that the stream could not be opened because the phone has no
     * zona yet.
     *
     * The route requires a positive `zona_id`, so this is a real, reachable
     * state — a collector whose zona has not arrived gets no push at all. Left
     * unreported it is indistinguishable from the outside from a stream that
     * opened and then died: both look like "pushes stopped", and one is a
     * provisioning gap while the other is an outage.
     *
     * Its own code rather than [ERROR_CODE_STREAM_CAIDO], for the same reason
     * the 503 has one: the two demand different responses from whoever reads
     * the telemetry. Correctness is unaffected either way — the visitas
     * `by-ids` channel carries no `zona_id`, so pending visitas are not
     * zone-scoped and the other three triggers reconcile them regardless. Only
     * push latency is lost.
     */
    fun onStreamHasNoZone() {
        errorReporter.report(
            code = ERROR_CODE_SIN_ZONA,
            message = CONTEXT_NO_ZONE,
            props = emptyMap()
        )
    }

    companion object {
        /**
         * The one SSE event name this build acts on. Must match the server's
         * `sseEventName` in `internal/visitas/infra/visitashttp/handlers_sse.go`.
         */
        const val EVENT_VISITAS_CONFIRMADAS: String = "visitas_confirmadas"

        /**
         * The reconciliation trigger threw. The push is lost this time; the
         * next trigger (periodic, at worst 15 minutes away) retries.
         */
        const val ERROR_CODE_EVENTO_FALLIDO: String = "visitas_push_evento_fallido"

        /**
         * The SSE stream dropped or could not be opened. Push is degraded;
         * app-open, periodic and connectivity still cover correctness.
         */
        const val ERROR_CODE_STREAM_CAIDO: String = "visitas_push_stream_caido"

        /** The server answered 503: visitas SSE is switched off server-side. */
        const val ERROR_CODE_STREAM_DESHABILITADO: String = "visitas_push_stream_deshabilitado"

        /**
         * No zona yet, so the stream cannot be opened. Push is unavailable for
         * this collector; the other three triggers still reconcile.
         */
        const val ERROR_CODE_SIN_ZONA: String = "visitas_push_sin_zona"

        /** Props key for the HTTP status that accompanied a stream failure. */
        const val PROP_HTTP_CODE: String = "http_code"

        private const val CONTEXT_ON_EVENT = "HandleVisitsPushEventUseCase.onEvent"
        private const val CONTEXT_STREAM = "HandleVisitsPushEventUseCase.onStreamFailure"
        private const val CONTEXT_STREAM_DISABLED =
            "HandleVisitsPushEventUseCase.onStreamDisabledByServer: 503"
        private const val CONTEXT_NO_ZONE =
            "HandleVisitsPushEventUseCase.onStreamHasNoZone: zona_id ausente"
        private const val NO_THROWABLE = "sin_throwable"
    }
}
