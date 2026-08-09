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
 *
 * @param id El UUID estable de la fila en `telemetry_events` ([TelemetryEventEntity.id],
 *   generado en Kotlin al encolar) — NO viaja dentro de [TelemetryEvent] (VO de dominio
 *   de Task 2, sin noción de cola). Fix ronda 1 de revisión: sin esto, un sink de red con
 *   entrega "al menos una vez" (reintentos tras timeout sin ack claro) no tiene clave de
 *   idempotencia/dedup del lado servidor — T4 lo necesita para no duplicar en el backend
 *   un evento que YA llegó pero cuyo ack se perdió en el camino de vuelta.
 * @param event El evento de dominio validado (name/props anti-PII, ver `TelemetryEvent`).
 */
fun interface TelemetrySink {
    suspend fun send(id: String, event: TelemetryEvent)
}
