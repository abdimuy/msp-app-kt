package com.example.msp_app.core.telemetry.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a `telemetry_events`. Sin lógica de negocio (backoff, límites de
 * reintento, sampling) — eso vive en [DurableTelemetryQueue], la política.
 * Este DAO es deliberadamente "tonto": persiste lo que se le pasa, filtra
 * por estado/orden, nada más. Todo timestamp que escribe llega como
 * parámetro desde Kotlin (`AppClock`), nunca `CURRENT_TIMESTAMP` de SQLite.
 */
@Dao
interface TelemetryEventDao {

    /** Insert síncrono (disco-primero): si esto no lanza, el evento sobrevive un crash. */
    @Insert
    suspend fun insert(event: TelemetryEventEntity)

    /**
     * Siguiente lote a drenar, en orden FIFO por [TelemetryEventEntity.createdAt].
     * Estados drenables: `PENDING` (nunca intentado) y `FAILED` (reintento).
     * `UPLOADING` deliberadamente NO se incluye acá — [recoverStuckUploading]
     * se llama SIEMPRE antes de este método en [DurableTelemetryQueue.drain],
     * así que cualquier fila que siga en `UPLOADING` para cuando se llega
     * acá ya fue reclamada de vuelta a `PENDING` (recuperación de un
     * `UPLOADING` colgado por un proceso que murió a mitad de un intento).
     */
    @Query(
        "SELECT * FROM telemetry_events WHERE state IN ('PENDING', 'FAILED') " +
            "ORDER BY createdAt ASC LIMIT :limit"
    )
    suspend fun nextBatch(limit: Int): List<TelemetryEventEntity>

    /** Marca el inicio de un intento de entrega — ack-based: no es `SENT` hasta que el sink confirme. */
    @Query(
        "UPDATE telemetry_events SET state = 'UPLOADING', lastAttemptAt = :now WHERE id IN (:ids)"
    )
    suspend fun markUploading(ids: List<String>, now: Long)

    /** El sink confirmó la entrega — único camino a `SENT` (ack real, nunca optimista). */
    @Query("UPDATE telemetry_events SET state = 'SENT' WHERE id IN (:ids)")
    suspend fun markSent(ids: List<String>)

    /**
     * El sink falló (o el intento se abandonó por límite de reintentos, ver
     * [DurableTelemetryQueue]) — vuelve a `FAILED`, incrementa
     * [TelemetryEventEntity.attemptCount] y agenda el próximo intento
     * elegible en [nextAttemptAt] (backoff, calculado en Kotlin).
     */
    @Query(
        "UPDATE telemetry_events SET state = 'FAILED', attemptCount = attemptCount + 1, " +
            "lastAttemptAt = :now, nextAttemptAt = :nextAttemptAt WHERE id IN (:ids)"
    )
    suspend fun markFailed(ids: List<String>, now: Long, nextAttemptAt: Long)

    /** Cuántos eventos aún no llegaron a `SENT` (pantalla de salud, lectura puntual). */
    @Query("SELECT COUNT(*) FROM telemetry_events WHERE state != 'SENT'")
    suspend fun pendingCount(): Int

    /** Igual que [pendingCount] pero reactivo — Room re-emite en cada write a la tabla. */
    @Query("SELECT COUNT(*) FROM telemetry_events WHERE state != 'SENT'")
    fun observePendingCount(): Flow<Int>

    /**
     * TTL de ruido: purga eventos `SENT` más viejos que [olderThan] (epoch millis,
     * calculado en Kotlin vía `AppClock`, nunca `julianday('now')` de SQLite).
     * `type != 'ERROR'` es el tier "nunca tirar errores" (spec §7.1): un evento
     * de error JAMÁS se borra por TTL, sin importar cuánto envejezca ni si
     * llegó a `SENT` — es rastro de auditoría, no ruido.
     */
    @Query(
        "DELETE FROM telemetry_events WHERE state = 'SENT' AND type != 'ERROR' AND createdAt < :olderThan"
    )
    suspend fun deleteSent(olderThan: Long): Int

    /**
     * Recuperación de `UPLOADING` colgado: dentro de UN proceso, la única
     * escritora de `UPLOADING` es [DurableTelemetryQueue.drain], que siempre
     * la resuelve a `SENT`/`FAILED` antes de retornar. Por lo tanto, si al
     * ARRANCAR un `drain()` hay filas en `UPLOADING`, son sobrevivientes de
     * un proceso anterior que murió a mitad de un intento (crash, kill de
     * la app) — nunca llegaron a ack. Se reclaman de vuelta a `PENDING` sin
     * tocar `attemptCount` (no cuenta como intento fallido; el intento ni
     * siquiera se sabe si llegó al sink) para que vuelvan a ser drenables
     * sin perderse y sin re-enviarse ya confirmadas (no había ack).
     */
    @Query("UPDATE telemetry_events SET state = 'PENDING' WHERE state = 'UPLOADING'")
    suspend fun recoverStuckUploading(): Int
}
