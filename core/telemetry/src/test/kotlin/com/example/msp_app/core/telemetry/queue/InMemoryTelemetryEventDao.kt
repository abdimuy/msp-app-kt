package com.example.msp_app.core.telemetry.queue

import java.util.Collections
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake en memoria de [TelemetryEventDao] (fakes-only, DISPATCH-CONVENTIONS.md
 * — no MockK). A diferencia de la DB Room real usada en el resto de
 * `DurableTelemetryQueueTest`, este fake NUNCA cambia de dispatcher/hilo — es
 * puro Kotlin mutando una lista en memoria, así que cada método "suspend"
 * resuelve de forma síncrona en el dispatcher del caller.
 *
 * Existe específicamente para los tests de cancelación estructurada: probar
 * que `CancellationException` se propaga desde [DurableTelemetryQueue.drain]
 * requiere que el ÚNICO punto de suspensión real dentro de la corrutina
 * lanzada sea el `sink.send(...)` que cuelga a propósito
 * ([HangingSink]/`awaitCancellation()`) — si los pasos previos del DAO
 * (`recoverStuckUploading`/`nextBatch`/`markUploading`) fueran contra la DB
 * Room real, saltarían a hilos reales del executor de Room, y
 * `advanceUntilIdle()` (que solo controla el scheduler VIRTUAL de
 * `runTest`) podría devolver el control ANTES de que esos pasos reales
 * terminaran — una carrera que haría el test flaky. Con este fake, todo lo
 * anterior a `sink.send` es instantáneo y determinista bajo `runTest`.
 */
class InMemoryTelemetryEventDao : TelemetryEventDao {

    private val rows: MutableList<TelemetryEventEntity> = Collections.synchronizedList(
        mutableListOf()
    )
    private val pendingCountFlow = MutableStateFlow(0)

    override suspend fun insert(event: TelemetryEventEntity) {
        rows += event
        publishPendingCount()
    }

    override suspend fun nextBatch(limit: Int): List<TelemetryEventEntity> = rows.filter {
        it.state == TelemetryEventState.PENDING.name || it.state == TelemetryEventState.FAILED.name
    }
        .take(limit)

    override suspend fun markUploading(ids: List<String>, now: Long) {
        updateRows(ids) { it.copy(state = TelemetryEventState.UPLOADING.name, lastAttemptAt = now) }
    }

    override suspend fun markSent(ids: List<String>) {
        updateRows(ids) { it.copy(state = TelemetryEventState.SENT.name) }
        publishPendingCount()
    }

    override suspend fun markFailed(ids: List<String>, now: Long, nextAttemptAt: Long) {
        updateRows(ids) {
            it.copy(
                state = TelemetryEventState.FAILED.name,
                attemptCount = it.attemptCount + 1,
                lastAttemptAt = now,
                nextAttemptAt = nextAttemptAt
            )
        }
    }

    override suspend fun pendingCount(): Int =
        rows.count { it.state != TelemetryEventState.SENT.name }

    override fun observePendingCount(): Flow<Int> = pendingCountFlow

    override suspend fun deleteSent(olderThan: Long): Int {
        val toRemove = rows.filter {
            it.state == TelemetryEventState.SENT.name && it.type != "ERROR" && it.createdAt < olderThan
        }
        rows.removeAll(toRemove)
        return toRemove.size
    }

    override suspend fun recoverStuckUploading(): Int {
        val stuck = rows.filter { it.state == TelemetryEventState.UPLOADING.name }
        updateRows(stuck.map { it.id }) { it.copy(state = TelemetryEventState.PENDING.name) }
        return stuck.size
    }

    private fun updateRows(
        ids: List<String>,
        transform: (TelemetryEventEntity) -> TelemetryEventEntity
    ) {
        val idSet = ids.toSet()
        val updated = rows.map { if (it.id in idSet) transform(it) else it }
        rows.clear()
        rows += updated
    }

    private fun publishPendingCount() {
        pendingCountFlow.value = rows.count { it.state != TelemetryEventState.SENT.name }
    }
}
