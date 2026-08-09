package com.example.msp_app.core.telemetry.queue

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake de [TelemetryEventDao] que lanza en TODOS sus métodos síncronos —
 * simula disco lleno / DB corrupta / cualquier fallo del store. Único
 * propósito: probar el invariante "nunca tira errores a callers" de
 * [DurableTelemetryQueue] sin depender de forzar una falla real de SQLite
 * (fakes-only, DISPATCH-CONVENTIONS.md — no MockK).
 */
class FailingTelemetryEventDao(
    private val failure: () -> Throwable = {
        IllegalStateException("fallo simulado del store de telemetria")
    }
) : TelemetryEventDao {

    override suspend fun insert(event: TelemetryEventEntity): Unit = throw failure()

    override suspend fun nextBatch(limit: Int): List<TelemetryEventEntity> = throw failure()

    override suspend fun markUploading(ids: List<String>, now: Long): Unit = throw failure()

    override suspend fun markSent(ids: List<String>): Unit = throw failure()

    override suspend fun markFailed(ids: List<String>, now: Long, nextAttemptAt: Long): Unit =
        throw failure()

    override suspend fun pendingCount(): Int = throw failure()

    override fun observePendingCount(): Flow<Int> = flow { throw failure() }

    override suspend fun deleteSent(olderThan: Long): Int = throw failure()

    override suspend fun recoverStuckUploading(): Int = throw failure()
}
