package com.example.msp_app.core.telemetry.adapter

import android.util.Log
import com.example.msp_app.core.telemetry.TelemetryEvent
import com.example.msp_app.core.telemetry.queue.DurableTelemetryQueue
import com.example.msp_app.core.telemetry.queue.TelemetrySink

private const val TAG = "StubTelemetrySink"

/**
 * Implementación STUB de [TelemetrySink] (Plan 4, Task 4). No hace red real:
 * loguea y retorna normalmente — lo que [DurableTelemetryQueue.drain]
 * interpreta como entrega confirmada (marca `SENT`, ver ack-based de esa
 * clase) — para que la cola durable tenga a dónde drenar sin depender de un
 * backend todavía.
 *
 * El sink real (GlitchTip/ingest propio + endpoint Go) es un spec de
 * observabilidad aparte (YAGNI para este plan, ver brief de Task 4) — llega
 * como una implementación NUEVA de [TelemetrySink] cableada en `di/TelemetryModule.kt`
 * en su momento, sin tocar [DurableTelemetryQueue] ni el puerto.
 */
class StubTelemetrySink : TelemetrySink {
    override suspend fun send(id: String, event: TelemetryEvent) {
        Log.d(TAG, "stub: evento '${event.name}' (${event.type}) marcado enviado, id=$id")
    }
}
