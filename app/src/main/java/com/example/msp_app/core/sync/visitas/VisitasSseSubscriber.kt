package com.example.msp_app.core.sync.visitas

import android.util.Log
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase
import com.example.msp_app.core.sync.cobranza.UserContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * Opens the visitas SSE stream and turns each event into a reconciliation
 * trigger — the fourth and fastest of the visits reconciler's triggers.
 *
 * ## Its own stream, not cobranza's
 *
 * The server serves visitas on a route of its own
 * (`/v2/visitas/sync/visitas/zona/{zona}/stream`) rather than adding an event
 * type to the pagos/saldos streams, and this class is the client half of that
 * decision. The reason was measured: the cobranza subscriber already in the
 * field reads the SSE event name into a log line
 * ([com.example.msp_app.core.sync.cobranza.CobranzaSseSubscriber], the
 * `onEvent` overrides) and then discards it, deciding what an event means from
 * which socket delivered it. A second event type on that stream would not have
 * broken old phones — it would have made every one of them run a full cobranza
 * cursor sync on every visit confirmed anywhere.
 *
 * ## This subscriber does discriminate
 *
 * Every event goes through [HandleVisitsPushEventUseCase.onEvent], which
 * switches on the event NAME and does nothing at all for a name it does not
 * recognise. That is the forward-compatibility property worth having:
 * surviving an unknown event is not enough if you then act on it.
 *
 * ## It marks nothing
 *
 * The event carries no visita ids and this class holds nothing that could
 * write one. It calls the trigger; the reconciler confirms through `by-ids`.
 * Task 10's single write path to "synced" is untouched.
 *
 * ## Failure behaviour
 *
 * - **503** — the server's `VISITAS_SSE_ENABLED` killswitch is off. Latch
 *   [featureFlagOff], stop retrying, report once. The other three triggers
 *   keep the reconciler correct; only latency degrades.
 * - **anything else** — exponential backoff 1s → 2s → 4s → 8s → 16s → 30s
 *   (cap), reset on each successful connection. Every failure reports.
 *
 * Lifecycle: [start] on ON_START, [stop] on ON_STOP. Both idempotent.
 */
class VisitasSseSubscriber(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String,
    private val userContextFlow: StateFlow<UserContext?>,
    private val handler: HandleVisitsPushEventUseCase,
    private val coroutineScope: CoroutineScope
) {
    private val mu = Object()

    private var source: EventSource? = null

    private var attempt = 0

    /** Job de observación de zona; se cancela en [stop]. */
    private var zoneWatchJob: Job? = null

    /**
     * Se latchea a true cuando el servidor responde 503. Una vez verdadero,
     * [start] es no-op — no tiene sentido reintentar contra un endpoint que
     * alguien apagó a propósito.
     */
    @Volatile
    private var featureFlagOff = false

    /** true mientras el stream esté activo (idempotencia de [start]). */
    @Volatile
    private var running = false

    // ─── API pública ─────────────────────────────────────────────────────────

    fun start() {
        if (featureFlagOff) {
            Log.i(TAG, "start: feature flag off — SSE de visitas deshabilitado")
            return
        }
        if (running) return
        running = true
        connect()
        zoneWatchJob = coroutineScope.launch {
            userContextFlow
                .distinctUntilChangedBy { it?.zona }
                // drop(1): el StateFlow re-emite el valor actual al suscribirnos
                // y `connect()` de arriba ya abrió el stream para esa zona. Sin
                // el drop se abre un segundo stream y se cancela el primero a
                // medio vivo — mismo razonamiento que CobranzaSseSubscriber.
                .drop(1)
                .collect { ctx ->
                    if (ctx != null) {
                        Log.i(TAG, "zona cambiada a ${ctx.zona} — reabriendo stream")
                        reconnect()
                    }
                }
        }
    }

    fun stop() {
        running = false
        zoneWatchJob?.cancel()
        zoneWatchJob = null
        synchronized(mu) {
            source?.cancel()
            source = null
        }
        Log.i(TAG, "stop: stream cancelado")
    }

    // ─── Conexión ────────────────────────────────────────────────────────────

    /**
     * La ruta exige un `zona_id` positivo, así que sin zona no hay stream —
     * el teléfono se queda con los otros tres disparadores hasta que la zona
     * llegue, momento en el que [zoneWatchJob] abre el stream. Esto NO es una
     * pérdida de correctitud: las visitas pendientes no están alcanzadas por
     * la zona (el `by-ids` de visitas va sin `zona_id`), solo la latencia del
     * push lo está.
     */
    private fun connect() {
        val zona = userContextFlow.value?.zona ?: run {
            Log.i(TAG, "connect: zona todavía null — esperando via zoneWatchJob")
            return
        }
        val path = "v2/visitas/sync/visitas/zona/$zona/stream"
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/" + path)
            .header("Accept", "text/event-stream")
            .build()
        val created = EventSources.createFactory(okHttpClient)
            .newEventSource(request, Listener())
        synchronized(mu) { source = created }
        Log.i(TAG, "SSE visitas conectando zona=$zona")
    }

    private fun reconnect() {
        synchronized(mu) {
            source?.cancel()
            source = null
            attempt = 0
        }
        connect()
    }

    // ─── Backoff ─────────────────────────────────────────────────────────────

    /** 1s, 2s, 4s, 8s, 16s, 30s, 30s, … */
    internal fun backoffMillis(attempt: Int): Long {
        val exp = minOf(attempt, MAX_BACKOFF_EXPONENT)
        return minOf(BASE_BACKOFF_MS shl exp, MAX_BACKOFF_MS)
    }

    // ─── Listener ────────────────────────────────────────────────────────────

    private inner class Listener : EventSourceListener() {

        override fun onOpen(eventSource: EventSource, response: Response) {
            Log.i(TAG, "SSE visitas abierto")
            synchronized(mu) { attempt = 0 }
        }

        /**
         * El `type` SÍ se usa — no es decoración. [HandleVisitsPushEventUseCase]
         * decide, y un nombre desconocido no dispara nada.
         */
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            coroutineScope.launch {
                val outcome = handler.onEvent(type)
                Log.i(TAG, "SSE visitas evento: type=$type outcome=$outcome")
            }
        }

        override fun onClosed(eventSource: EventSource) {
            Log.i(TAG, "SSE visitas cerrado (cliente)")
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            if (response?.code == HTTP_SERVICE_UNAVAILABLE) {
                Log.w(TAG, "SSE visitas: 503 — flag apagado en el servidor, deteniendo")
                synchronized(mu) {
                    featureFlagOff = true
                    source = null
                }
                handler.onStreamDisabledByServer()
                return
            }
            if (!running) return

            // Un stream que se cae sin reportar es invisible por construcción:
            // el único síntoma sería que las visitas se reconcilian en el tick
            // de 15 minutos en vez de en segundos.
            handler.onStreamFailure(t, response?.code)

            val current: Int
            synchronized(mu) {
                current = attempt
                attempt++
            }
            val wait = backoffMillis(current)
            Log.w(TAG, "SSE visitas falló (attempt=$current) — reintento en ${wait}ms")
            coroutineScope.launch {
                delay(wait)
                if (running && !featureFlagOff) connect()
            }
        }
    }

    companion object {
        private const val TAG = "VisitasSseSubscriber"

        private const val HTTP_SERVICE_UNAVAILABLE = 503

        /** Delay base del backoff exponencial. */
        const val BASE_BACKOFF_MS = 1_000L

        /** Cap del backoff: no superar 30s. */
        const val MAX_BACKOFF_MS = 30_000L

        /** 2^5 = 32s → cap a 30s. */
        private const val MAX_BACKOFF_EXPONENT = 5
    }
}
