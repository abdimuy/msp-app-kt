package com.example.msp_app.core.telemetry.queue

/**
 * Estados del ciclo de vida de un [TelemetryEventEntity] dentro de la cola
 * durable (spec Plan 4 §7.1, ack-based, espejo del outbox de pagos
 * endurecido de `:core:common`).
 *
 * Transiciones válidas:
 * - `PENDING` → `UPLOADING` (arranca un intento de drenado, [DurableTelemetryQueue.drain]).
 * - `UPLOADING` → `SENT` (el [TelemetrySink] confirmó la entrega — ack real).
 * - `UPLOADING` → `FAILED` (el sink lanzó/reportó fallo — se reintentará con backoff).
 * - `UPLOADING` → `PENDING` (recuperación de un `UPLOADING` colgado: el proceso
 *   murió a mitad de un intento anterior; ver [TelemetryEventDao.recoverStuckUploading]).
 * - `FAILED` → `UPLOADING` (reintento tras backoff, mismo camino que `PENDING`).
 *
 * `SENT` es terminal salvo purga por TTL ([TelemetryEventDao.deleteSent]) — y
 * esa purga NUNCA alcanza eventos `TelemetryEventType.ERROR` (tier "nunca
 * tirar errores", ver KDoc de [TelemetryEventDao.deleteSent]).
 */
enum class TelemetryEventState {
    PENDING,
    UPLOADING,
    SENT,
    FAILED
}
