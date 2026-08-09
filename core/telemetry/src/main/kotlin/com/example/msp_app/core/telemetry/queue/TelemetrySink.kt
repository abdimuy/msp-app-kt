package com.example.msp_app.core.telemetry.queue

import com.example.msp_app.core.telemetry.TelemetryEvent

/**
 * Puerto de salida de la cola durable: "entregar este evento a donde sea que
 * vaya" (red, T4). [DurableTelemetryQueue.drain] no sabe ni le importa qué
 * hay detrás — HTTP, un logger remoto, un stub de test.
 *
 * Contrato: [send] señaliza éxito retornando normalmente y fallo LANZANDO.
 * No hay un `Result`/`Boolean` de retorno a propósito — es el mismo idioma
 * que usan los `PendingWorkSynchronizer` de `:core:common` (ack-based: el
 * caller decide qué hacer con la excepción, acá "reintentar con backoff").
 * La implementación real de este puerto llega en T4 (stub de red); en este
 * plan solo hay fakes de test (`RecordingSink`), fieles a "fakes-only, no
 * mocks" (DISPATCH-CONVENTIONS.md).
 */
fun interface TelemetrySink {
    suspend fun send(event: TelemetryEvent)
}
