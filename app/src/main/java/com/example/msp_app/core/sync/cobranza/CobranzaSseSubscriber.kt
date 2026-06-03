package com.example.msp_app.core.sync.cobranza

import android.util.Log
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
 * Tipo del stream SSE que disparó el evento: pagos o saldos.
 * El callback [CobranzaSseSubscriber.onEvent] recibe el kind para que el caller
 * pueda enrutar al endpoint correcto (pagosByIds vs saldosByIds).
 */
enum class SseKind { PAGOS, SALDOS }

/**
 * Abre dos streams SSE contra el backend v2 (pagos + saldos) y dispara
 * [onEvent] ante cualquier notificación del servidor. El payload del evento
 * incluye los IDs afectados en el campo `ids`, que se pasan al callback para
 * permitir un fetch quirúrgico en lugar de un re-sync completo.
 *
 * Comportamiento ante fallos:
 *  - 503: el feature flag está apagado en el servidor; se latchea
 *    [featureFlagOff] = true, se cancela el otro stream y NO se reintenta.
 *    El polling+reconcile existente continúa cubriendo la funcionalidad.
 *  - Otros fallos (red, 5xx, etc.): reconexión con backoff exponencial
 *    1s → 2s → 4s → 8s → 16s → 30s (cap). Se resetea a 1s en cada
 *    conexión exitosa.
 *
 * Eventos en ráfaga: se debouncea [DEBOUNCE_MS] = 100ms. Durante la ventana
 * de debounce, todos los IDs recibidos se acumulan y el callback recibe la
 * unión de todos los IDs del kind (pagos y saldos se despachan por separado).
 *
 * Ciclo de vida: llamar [start] en ON_START y [stop] en ON_STOP. Ambos
 * son idempotentes.
 */
class CobranzaSseSubscriber(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String,
    private val userContextFlow: StateFlow<UserContext?>,
    private val onEvent: suspend (kind: SseKind, ids: List<Int>) -> Unit,
    private val coroutineScope: CoroutineScope,
    /** Inyectable para tests: devuelve el epoch ms actual. */
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val mu = Object()

    @GuardedBy("mu")
    private var pagoSource: EventSource? = null

    @GuardedBy("mu")
    private var saldoSource: EventSource? = null

    @GuardedBy("mu")
    private var pagoAttempt = 0

    @GuardedBy("mu")
    private var saldoAttempt = 0

    @GuardedBy("mu")
    private var pagosDebounceJob: Job? = null

    @GuardedBy("mu")
    private var saldosDebounceJob: Job? = null

    /**
     * IDs acumulados durante la ventana de debounce de pagos. Se limpia al
     * despachar y se une con la siguiente ráfaga.
     */
    @GuardedBy("mu")
    private val pendingPagosIds = mutableListOf<Int>()

    /**
     * IDs acumulados durante la ventana de debounce de saldos.
     */
    @GuardedBy("mu")
    private val pendingSaldosIds = mutableListOf<Int>()

    /**
     * Se latchea a true cuando el servidor responde 503 (feature flag off).
     * Una vez verdadero, [start] es no-op — no tiene sentido seguir
     * intentando conectarse a un endpoint deshabilitado.
     */
    @Volatile private var featureFlagOff = false

    /** Coroutine que observa cambios de zona y reabre los streams. */
    private var zoneWatchJob: Job? = null

    /** true mientras los streams estén activos (para idempotencia de start). */
    @Volatile private var running = false

    // ─── API pública ─────────────────────────────────────────────────────────

    /**
     * Conecta los dos streams SSE. Idempotente: si ya están corriendo o el
     * feature flag está apagado, no hace nada.
     */
    fun start() {
        if (featureFlagOff) {
            Log.i(TAG, "start: feature flag off — SSE deshabilitado, usando polling")
            return
        }
        if (running) return
        running = true
        connectBoth()
        zoneWatchJob = coroutineScope.launch {
            userContextFlow
                .distinctUntilChangedBy { it?.zona }
                // drop(1): el StateFlow re-emite el valor actual al suscribirnos,
                // y `connectBoth()` arriba ya abrió los streams para esa zona.
                // Sin el drop, el primer `collect` dispara un `reconnectBoth`
                // inmediato — abre un segundo par de streams y cancela el
                // primero medio-vivo. Net result era el mismo (sobrevive solo
                // el par B), pero con ventana de doble conexión y latency
                // extra al inicio.
                .drop(1)
                .collect { ctx ->
                    if (ctx != null) {
                        Log.i(TAG, "zona cambiada a ${ctx.zona} — reabriendo streams")
                        reconnectBoth()
                    }
                }
        }
    }

    /**
     * Cancela ambos streams y detiene el job de observación de zona. Idempotente.
     */
    fun stop() {
        running = false
        zoneWatchJob?.cancel()
        zoneWatchJob = null
        synchronized(mu) {
            pagoSource?.cancel()
            pagoSource = null
            saldoSource?.cancel()
            saldoSource = null
            pagosDebounceJob?.cancel()
            pagosDebounceJob = null
            saldosDebounceJob?.cancel()
            saldosDebounceJob = null
            pendingPagosIds.clear()
            pendingSaldosIds.clear()
        }
        Log.i(TAG, "stop: streams cancelados")
    }

    // ─── Conexión ────────────────────────────────────────────────────────────

    private fun connectBoth() {
        val zona = userContextFlow.value?.zona ?: run {
            Log.i(TAG, "connectBoth: zona todavía null — esperando zona via zoneWatchJob")
            return
        }
        connectPagos(zona)
        connectSaldos(zona)
    }

    private fun reconnectBoth() {
        synchronized(mu) {
            pagoSource?.cancel()
            pagoSource = null
            pagoAttempt = 0
            saldoSource?.cancel()
            saldoSource = null
            saldoAttempt = 0
        }
        connectBoth()
    }

    private fun connectPagos(zona: Int) {
        val path = "v2/cobranza/sync/pagos/zona/$zona/stream"
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/" + path)
            .header("Accept", "text/event-stream")
            .build()
        val source = EventSources.createFactory(okHttpClient)
            .newEventSource(request, PagosListener())
        synchronized(mu) { pagoSource = source }
        Log.i(TAG, "SSE pagos conectando zona=$zona")
    }

    private fun connectSaldos(zona: Int) {
        val path = "v2/cobranza/sync/saldos/zona/$zona/stream"
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/" + path)
            .header("Accept", "text/event-stream")
            .build()
        val source = EventSources.createFactory(okHttpClient)
            .newEventSource(request, SaldosListener())
        synchronized(mu) { saldoSource = source }
        Log.i(TAG, "SSE saldos conectando zona=$zona")
    }

    // ─── Debounce ────────────────────────────────────────────────────────────

    /**
     * Acumula los IDs recibidos y programa una llamada a [onEvent] para este
     * kind tras la ventana de debounce. Si llegan más eventos del mismo kind
     * antes de que expire la ventana, los IDs se unen y el job se reinicia.
     *
     * Loguea el transit time (server→cliente) y el tiempo total hasta que
     * [onEvent] retorna (incluye debounce + fetch). Permite medir end-to-end
     * real-time desde logcat sin recursos extras.
     *
     * @param eventReceivedAt  Timestamp del momento en que llegó el evento.
     * @param kind             Stream de origen (pagos o saldos).
     * @param incomingIds      IDs incluidos en este evento (puede ser null si
     *                         el servidor no los incluyó, o vacío si el campo
     *                         `ids` era `[]`).
     */
    private fun scheduleOnEvent(eventReceivedAt: Long, kind: SseKind, incomingIds: List<Int>?) {
        synchronized(mu) {
            val pendingIds = if (kind == SseKind.PAGOS) pendingPagosIds else pendingSaldosIds
            if (incomingIds != null) {
                pendingIds.addAll(incomingIds)
            }

            val jobRef: Job?
            if (kind == SseKind.PAGOS) {
                pagosDebounceJob?.cancel()
                jobRef = coroutineScope.launch {
                    delay(DEBOUNCE_MS)
                    val idsToDispatch: List<Int>
                    synchronized(mu) {
                        idsToDispatch = pendingPagosIds.toList()
                        pendingPagosIds.clear()
                    }
                    val syncStartedAt = System.currentTimeMillis()
                    onEvent(SseKind.PAGOS, idsToDispatch)
                    val syncEndedAt = System.currentTimeMillis()
                    Log.i(
                        TAG,
                        "SSE pagos sync done: ids=${idsToDispatch.size} " +
                            "sync=${syncEndedAt - syncStartedAt}ms " +
                            "total_since_event=${syncEndedAt - eventReceivedAt}ms"
                    )
                }
                pagosDebounceJob = jobRef
            } else {
                saldosDebounceJob?.cancel()
                jobRef = coroutineScope.launch {
                    delay(DEBOUNCE_MS)
                    val idsToDispatch: List<Int>
                    synchronized(mu) {
                        idsToDispatch = pendingSaldosIds.toList()
                        pendingSaldosIds.clear()
                    }
                    val syncStartedAt = System.currentTimeMillis()
                    onEvent(SseKind.SALDOS, idsToDispatch)
                    val syncEndedAt = System.currentTimeMillis()
                    Log.i(
                        TAG,
                        "SSE saldos sync done: ids=${idsToDispatch.size} " +
                            "sync=${syncEndedAt - syncStartedAt}ms " +
                            "total_since_event=${syncEndedAt - eventReceivedAt}ms"
                    )
                }
                saldosDebounceJob = jobRef
            }
        }
    }

    /**
     * Extrae `ts` (millis epoch UTC) y `ids` (lista de Int) del payload SSE
     * `data: {"ts":N,"ids":[1,2,3]}`.
     *
     * - `ts` ausente o malformado → null.
     * - `ids` ausente → null (señal de que el servidor no envió el campo).
     * - `ids` presente pero vacío `[]` → emptyList().
     *
     * Sin alocar JSONObject: regex simple para no traer dependencias.
     *
     * @return Par (ts, ids). Ambos campos son independientes y pueden ser null.
     */
    internal fun parseServerTsAndIds(data: String): Pair<Long?, List<Int>?> {
        val ts = Regex("\"ts\"\\s*:\\s*(\\d+)").find(data)
            ?.groupValues?.get(1)?.toLongOrNull()

        val idsMatch = Regex("\"ids\"\\s*:\\s*\\[([^\\]]*)\\]").find(data)
        val ids: List<Int>? = if (idsMatch == null) {
            null
        } else {
            val inner = idsMatch.groupValues[1].trim()
            if (inner.isEmpty()) {
                emptyList()
            } else {
                inner.split(",").mapNotNull { it.trim().toIntOrNull() }
            }
        }

        return Pair(ts, ids)
    }

    // ─── Backoff ─────────────────────────────────────────────────────────────

    /**
     * Calcula el tiempo de espera antes del intento número [attempt] (base 0).
     * Esquema: 1s, 2s, 4s, 8s, 16s, 30s, 30s, …
     */
    internal fun backoffMillis(attempt: Int): Long {
        val exp = minOf(attempt, MAX_BACKOFF_EXPONENT)
        return minOf(BASE_BACKOFF_MS shl exp, MAX_BACKOFF_MS)
    }

    // ─── Listeners ───────────────────────────────────────────────────────────

    private inner class PagosListener : EventSourceListener() {

        override fun onOpen(eventSource: EventSource, response: Response) {
            Log.i(TAG, "SSE pagos abierto")
            synchronized(mu) { pagoAttempt = 0 }
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            val receivedAt = System.currentTimeMillis()
            val (serverTs, ids) = parseServerTsAndIds(data)
            val transit = serverTs?.let { receivedAt - it }
            Log.i(
                TAG,
                "SSE pagos evento: type=$type ids=${ids?.size ?: "?"} transit=${transit ?: "?"}ms"
            )
            scheduleOnEvent(receivedAt, SseKind.PAGOS, ids)
        }

        override fun onClosed(eventSource: EventSource) {
            // Cierre solicitado por el cliente (stop()) — no reconectar.
            Log.i(TAG, "SSE pagos cerrado (cliente)")
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            if (response?.code == 503) {
                Log.w(TAG, "SSE pagos: 503 — feature flag off, deteniendo SSE")
                // Latch + cancel del otro stream en un solo crítico section
                // para que el SaldosListener.onFailure (que puede correr
                // concurrentemente en otro thread del pool de OkHttp) lea
                // featureFlagOff=true antes de programar su propio reintento.
                synchronized(mu) {
                    featureFlagOff = true
                    saldoSource?.cancel()
                    saldoSource = null
                }
                return
            }
            if (!running) return
            val attempt: Int
            synchronized(mu) {
                attempt = pagoAttempt
                pagoAttempt++
            }
            val delay = backoffMillis(attempt)
            Log.w(
                TAG,
                "SSE pagos falló (attempt=$attempt) — reintento en ${delay}ms: ${t?.message}"
            )
            coroutineScope.launch {
                delay(delay)
                if (running && !featureFlagOff) {
                    userContextFlow.value?.zona?.let { connectPagos(it) }
                }
            }
        }
    }

    private inner class SaldosListener : EventSourceListener() {

        override fun onOpen(eventSource: EventSource, response: Response) {
            Log.i(TAG, "SSE saldos abierto")
            synchronized(mu) { saldoAttempt = 0 }
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            val receivedAt = System.currentTimeMillis()
            val (serverTs, ids) = parseServerTsAndIds(data)
            val transit = serverTs?.let { receivedAt - it }
            Log.i(
                TAG,
                "SSE saldos evento: type=$type ids=${ids?.size ?: "?"} transit=${transit ?: "?"}ms"
            )
            scheduleOnEvent(receivedAt, SseKind.SALDOS, ids)
        }

        override fun onClosed(eventSource: EventSource) {
            Log.i(TAG, "SSE saldos cerrado (cliente)")
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            if (response?.code == 503) {
                Log.w(TAG, "SSE saldos: 503 — feature flag off, deteniendo SSE")
                // Mismo crítico-sección único que el PagosListener — ver allá.
                synchronized(mu) {
                    featureFlagOff = true
                    pagoSource?.cancel()
                    pagoSource = null
                }
                return
            }
            if (!running) return
            val attempt: Int
            synchronized(mu) {
                attempt = saldoAttempt
                saldoAttempt++
            }
            val delay = backoffMillis(attempt)
            Log.w(
                TAG,
                "SSE saldos falló (attempt=$attempt) — reintento en ${delay}ms: ${t?.message}"
            )
            coroutineScope.launch {
                delay(delay)
                if (running && !featureFlagOff) {
                    userContextFlow.value?.zona?.let { connectSaldos(it) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "CobranzaSseSubscriber"

        /** Ventana de coalescencia: múltiples eventos en 100ms se colapsan en uno. */
        const val DEBOUNCE_MS = 100L

        /** Delay base del backoff exponencial. */
        const val BASE_BACKOFF_MS = 1_000L

        /** Cap del backoff: no superar 30s. */
        const val MAX_BACKOFF_MS = 30_000L

        /**
         * Número máximo de veces que se dobla el base: 2^5=32s → cap a 30s.
         * Con 6 bits: 1s, 2s, 4s, 8s, 16s, 32s→30s, 30s, …
         */
        private const val MAX_BACKOFF_EXPONENT = 5
    }
}

// Anotación documental; Android no impone su semántica en coroutines, pero
// ayuda a los revisores a identificar campos protegidos por el monitor `mu`.
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
private annotation class GuardedBy(val value: String)
