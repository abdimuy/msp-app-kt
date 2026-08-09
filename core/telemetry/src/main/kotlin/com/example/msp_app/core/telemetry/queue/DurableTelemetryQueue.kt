package com.example.msp_app.core.telemetry.queue

import android.util.Log
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.telemetry.TelemetryEvent
import com.example.msp_app.core.telemetry.TelemetryEventType
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

private const val TAG = "DurableTelemetryQueue"

/**
 * Política de la cola durable de telemetría (spec Plan 4 §7.1) — el corazón
 * de este módulo. [TelemetryEventDao] es el store tonto; esta clase decide
 * CUÁNDO/CÓMO se drena, con 3 invariantes duros que TODOS sus métodos
 * respetan:
 *
 *  1. **Durable**: [enqueue] hace un insert síncrono disco-primero antes de
 *     retornar — si el proceso muere justo después, el evento ya está en
 *     `telemetry_events` (ver `DurableTelemetryQueueTest`, prueba de
 *     cerrar/reabrir la DB real).
 *  2. **FIFO**: [drain] toma el lote de [TelemetryEventDao.nextBatch]
 *     (ordenado por `rowid`, NO por `createdAt` — fix ronda 1 de revisión:
 *     dos eventos encolados en el MISMO milisegundo tenían orden indefinido
 *     ordenando por timestamp; `rowid` es el id de inserción monótono e
 *     intrínseco de SQLite, nunca ambiguo entre dos inserts) y lo entrega al
 *     [sink] en ESE orden, sin reordenar ni paralelizar.
 *  3. **Nunca tira errores al caller, PERO `CancellationException` SIEMPRE se
 *     repropaga**: un fallo del DAO (disco lleno, DB corrupta) o del [sink]
 *     (red caída) se loguea y se absorbe — telemetría es best-effort por
 *     diseño, una falla acá NUNCA debe tumbar el flujo de negocio que la está
 *     emitiendo. Es la política opuesta al outbox de dinero (`:core:common`
 *     `sync/pendingwork`) — ahí tragarse un error sería el bug; acá es el
 *     requisito. PERO "nunca tira errores" NO incluye la cancelación
 *     estructurada: cada `catch (e: Throwable)` de esta clase repropaga
 *     `CancellationException` ANTES de absorber cualquier otra cosa (fix
 *     ronda 1: un `catch(Throwable)` ciego también atrapaba
 *     `CancellationException`, rompiendo la cancelación cooperativa — un
 *     scope cancelado no lograba cancelar de verdad este trabajo).
 *
 * @param dao Store Room propio de `telemetry_db` (ver [TelemetryDatabase]).
 * @param clock Fuente de tiempo inyectable — SIEMPRE `AppClock`, nunca
 *   `Instant.now()` directo, para tests deterministas con `FakeClock`.
 */
class DurableTelemetryQueue(
    private val dao: TelemetryEventDao,
    private val clock: AppClock = AppClock.System
) {

    /**
     * Encola [event] — insert síncrono disco-primero. El `id` es un UUID
     * generado acá en Kotlin (nunca autogen de Room/SQLite, ver gotcha del
     * brief). Si el insert falla (DB corrupta, disco lleno), el evento se
     * pierde SILENCIOSAMENTE — nunca lanza — porque perder un evento de
     * telemetría es tolerable; tumbar la operación de negocio que lo emitió
     * no lo es.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // "nunca tirar errores a callers" (spec §7.1) — intencional.
    suspend fun enqueue(event: TelemetryEvent) {
        try {
            val now = clock.now().toEpochMilli()
            dao.insert(
                TelemetryEventEntity(
                    id = UUID.randomUUID().toString(),
                    type = event.type.name,
                    name = event.name,
                    propsJson = TelemetryPropsCodec.encode(event.props),
                    occurredAt = event.occurredAt.toEpochMilli(),
                    state = TelemetryEventState.PENDING.name,
                    attemptCount = 0,
                    lastAttemptAt = null,
                    nextAttemptAt = now,
                    createdAt = now
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "no se pudo encolar el evento de telemetria (descartado, best-effort)", e)
        }
    }

    /**
     * Conteo reactivo de eventos aún no `SENT`, para el widget de salud.
     * Pass-through directo al `Flow` de Room: suscribirse no lanza de forma
     * síncrona (Room solo emite/re-emite al invalidar la tabla), así que no
     * hay nada que envolver acá — a diferencia de [enqueue]/[drain]/[pendingCount],
     * que sí hacen trabajo síncrono que puede fallar.
     */
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    /** Lectura puntual del mismo conteo que [observePendingCount]. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun pendingCount(): Int = try {
        dao.pendingCount()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.e(TAG, "no se pudo leer el conteo pendiente de telemetria", e)
        0
    }

    /** TTL de ruido — ver [TelemetryEventDao.deleteSent] para la exención de `type=ERROR`. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun purgeSentNoise(olderThan: Instant) {
        try {
            dao.deleteSent(olderThan.toEpochMilli())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "no se pudo purgar telemetria enviada (no critico)", e)
        }
    }

    /**
     * Drena hasta [batchSize] eventos, en orden FIFO, hacia [sink]. Devuelve
     * cuántos se entregaron con éxito (0 si la cola está vacía, si nada está
     * "due" todavía por backoff, o si algo internamente falló — nunca lanza).
     *
     * Flujo:
     *  1. [TelemetryEventDao.recoverStuckUploading] — reclama `UPLOADING`
     *     colgado de un proceso anterior antes de elegir el lote.
     *  2. [TelemetryEventDao.nextBatch] — el lote FIFO.
     *  3. Filtra por [TelemetryEventEntity.nextAttemptAt] (backoff): un
     *     `FAILED` reciente que todavía no cumplió su espera no se toca.
     *  4. Marca el lote elegible como `UPLOADING` (ack pendiente).
     *  5. Por cada evento, EN ORDEN: intenta [TelemetrySink.send]; si
     *     confirma, `SENT`; si falla, [registerFailure] (backoff o
     *     abandono, ver esa función).
     *
     * Cualquier excepción del [dao] en los pasos 1/2/4 aborta el resto del
     * lote (los eventos ya marcados `UPLOADING` se recuperan en el próximo
     * `drain()`, paso 1) pero NUNCA se propaga fuera de esta función.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun drain(
        sink: TelemetrySink,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
    ): Int = try {
        dao.recoverStuckUploading()
        val batch = dao.nextBatch(batchSize)
        val now = clock.now()
        val due = batch.filter { it.nextAttemptAt <= now.toEpochMilli() }

        if (due.isEmpty()) {
            0
        } else {
            dao.markUploading(due.map { it.id }, now.toEpochMilli())
            due.sumOf { entity -> attemptDelivery(entity, sink, maxAttempts) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.e(TAG, "fallo inesperado drenando la cola de telemetria (best-effort, sin propagar)", e)
        0
    }

    /**
     * Entrega UN evento al [sink]. El `try/catch` alrededor de [TelemetrySink.send]
     * es el corazón del ack-based: solo un `send` que retorna normalmente
     * cuenta como éxito — cualquier excepción (red, timeout, lo que sea) se
     * trata como fallo de entrega, nunca como crash del drenado.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // "nunca tirar errores a callers" (spec §7.1) — intencional.
    private suspend fun attemptDelivery(
        entity: TelemetryEventEntity,
        sink: TelemetrySink,
        maxAttempts: Int
    ): Int {
        val delivered = try {
            // `entity.id` (el UUID estable de la fila) viaja como clave de dedup
            // para el sink (T4, entrega al menos una vez por red) — ver
            // `TelemetrySink.send`.
            sink.send(entity.id, entity.toDomainEvent())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "el sink de telemetria fallo para ${entity.id}, se reintentara", e)
            false
        }

        return if (delivered) {
            dao.markSent(listOf(entity.id))
            1
        } else {
            registerFailure(entity, maxAttempts)
            0
        }
    }

    /**
     * Tras un fallo de entrega: si el evento es `type=ERROR`, SIEMPRE se
     * reintenta (tier "nunca tirar errores", spec §7.1) — solo backoff, sin
     * tope de intentos. Para cualquier otro tipo (`SCREEN_VIEW`/`TAP`/`EVENT`,
     * "ruido"), al llegar a [maxAttempts] se abandona: se marca `SENT` para
     * sacarlo de la cabeza del FIFO (si no, un evento envenenado bloquearía
     * para siempre el drenado de todo lo que viene detrás) y queda elegible
     * para el TTL normal de [TelemetryEventDao.deleteSent] como cualquier
     * `SENT` — es "best-effort tirar el resto con sampling" del brief: se
     * tira SOLO ruido no crítico, nunca un error.
     */
    private suspend fun registerFailure(entity: TelemetryEventEntity, maxAttempts: Int) {
        val nextAttemptCount = entity.attemptCount + 1
        val isError = entity.type == TelemetryEventType.ERROR.name

        if (!isError && nextAttemptCount >= maxAttempts) {
            dao.markSent(listOf(entity.id))
            return
        }

        val now = clock.now()
        dao.markFailed(
            listOf(entity.id),
            now.toEpochMilli(),
            backoffFrom(now, nextAttemptCount).toEpochMilli()
        )
    }

    /** Backoff exponencial con tope: `BASE * 2^(intentos-1)`, capado en [MAX_BACKOFF_SECONDS]. */
    private fun backoffFrom(now: Instant, attemptCount: Int): Instant {
        val exponent = (attemptCount - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        val seconds = (BASE_BACKOFF_SECONDS shl exponent).coerceAtMost(MAX_BACKOFF_SECONDS)
        return now.plusSeconds(seconds)
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 25
        const val DEFAULT_MAX_ATTEMPTS = 8
        private const val BASE_BACKOFF_SECONDS = 30L
        private const val MAX_BACKOFF_SECONDS = 900L // 15 min
        private const val MAX_BACKOFF_EXPONENT = 6
    }
}

private fun TelemetryEventEntity.toDomainEvent(): TelemetryEvent = TelemetryEvent(
    type = TelemetryEventType.valueOf(type),
    name = name,
    occurredAt = Instant.ofEpochMilli(occurredAt),
    props = TelemetryPropsCodec.decode(propsJson)
)
