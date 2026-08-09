package com.example.msp_app.core.telemetry.queue

import com.example.msp_app.core.telemetry.TelemetryEvent
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fake de [TelemetrySink] (fakes-only, DISPATCH-CONVENTIONS.md — no MockK).
 * Graba en orden cada evento que [DurableTelemetryQueue.drain] le entrega
 * exitosamente y opcionalmente simula fallas vía [failFor]: los `name` de
 * evento listados ahí lanzan en vez de "entregar", tantas veces como
 * [failFor] indique, para poder probar reintento + backoff sin acoplarse a
 * temporizadores reales.
 */
class RecordingSink(private val failFor: Map<String, Int> = emptyMap()) : TelemetrySink {

    private val mutableSent: MutableList<TelemetryEvent> = Collections.synchronizedList(
        mutableListOf()
    )
    private val remainingFailures: MutableMap<String, AtomicInteger> =
        failFor.mapValues { (_, count) -> AtomicInteger(count) }.toMutableMap()

    val sent: List<TelemetryEvent> get() = mutableSent.toList()

    val callCount = AtomicInteger(0)

    override suspend fun send(event: TelemetryEvent) {
        callCount.incrementAndGet()
        val remaining = remainingFailures[event.name]
        if (remaining != null && remaining.get() > 0) {
            remaining.decrementAndGet()
            throw IOException("fallo simulado de red para ${event.name}")
        }
        mutableSent += event
    }
}

/** Sink que SIEMPRE falla — para probar que un sink caído nunca tumba [DurableTelemetryQueue.drain]. */
class AlwaysFailingSink : TelemetrySink {
    override suspend fun send(event: TelemetryEvent): Nothing =
        throw IOException("sink de telemetria caido")
}
