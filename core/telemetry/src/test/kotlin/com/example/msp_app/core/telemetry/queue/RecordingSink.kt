package com.example.msp_app.core.telemetry.queue

import com.example.msp_app.core.telemetry.TelemetryEvent
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/**
 * Fake de [TelemetrySink] (fakes-only, DISPATCH-CONVENTIONS.md — no MockK).
 * Graba en orden cada `(id, event)` que [DurableTelemetryQueue.drain] le
 * entrega exitosamente — el `id` (fix ronda 1: dedup key para T4) se graba
 * en paralelo a [sent] vía [deliveredIds]. Opcionalmente simula fallas vía
 * [failFor]: los `name` de evento listados ahí lanzan en vez de "entregar",
 * tantas veces como [failFor] indique, para poder probar reintento + backoff
 * sin acoplarse a temporizadores reales. [delayMillis] (fix ronda 1, test de
 * concurrencia) mete un `delay()` real ANTES de completar cada `send`, para
 * abrir una ventana de solapamiento real cuando el test corre bajo un
 * dispatcher de verdad (`runBlocking` + `Dispatchers.Default`), no bajo el
 * scheduler virtual de `runTest`.
 */
class RecordingSink(
    private val failFor: Map<String, Int> = emptyMap(),
    private val delayMillis: Long = 0
) : TelemetrySink {

    private val mutableSent: MutableList<TelemetryEvent> = Collections.synchronizedList(
        mutableListOf()
    )
    private val mutableDeliveredIds: MutableList<String> = Collections.synchronizedList(
        mutableListOf()
    )
    private val remainingFailures: MutableMap<String, AtomicInteger> =
        failFor.mapValues { (_, count) -> AtomicInteger(count) }.toMutableMap()

    val sent: List<TelemetryEvent> get() = mutableSent.toList()
    val deliveredIds: List<String> get() = mutableDeliveredIds.toList()

    val callCount = AtomicInteger(0)

    override suspend fun send(id: String, event: TelemetryEvent) {
        callCount.incrementAndGet()
        if (delayMillis > 0) delay(delayMillis)
        val remaining = remainingFailures[event.name]
        if (remaining != null && remaining.get() > 0) {
            remaining.decrementAndGet()
            throw IOException("fallo simulado de red para ${event.name}")
        }
        mutableSent += event
        mutableDeliveredIds += id
    }
}

/** Sink que SIEMPRE falla — para probar que un sink caído nunca tumba [DurableTelemetryQueue.drain]. */
class AlwaysFailingSink : TelemetrySink {
    override suspend fun send(id: String, event: TelemetryEvent): Nothing =
        throw IOException("sink de telemetria caido")
}

/**
 * Sink que se cuelga indefinidamente (nunca completa ni falla) — usado para
 * probar que cancelar el `Job`/`CoroutineScope` que llama a
 * [DurableTelemetryQueue.drain] SÍ cancela de verdad el trabajo (fix ronda 1:
 * `CancellationException` ya no se traga). `awaitCancellation()` es el punto
 * de suspensión "puro" recomendado por kotlinx.coroutines exactamente para
 * este propósito de test.
 */
class HangingSink : TelemetrySink {
    override suspend fun send(id: String, event: TelemetryEvent): Nothing = awaitCancellation()
}
