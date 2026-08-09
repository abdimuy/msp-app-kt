package com.example.msp_app.core.telemetry.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fila persistida de la cola durable de telemetría (`telemetry_db`, store
 * PROPIO — ver [TelemetryDatabase], NUNCA `msp_db`/v27 de `:core:database`).
 * Es la representación en disco de un [com.example.msp_app.core.telemetry.TelemetryEvent]
 * ya encolado, más el estado de drenado que el VO de dominio no modela.
 *
 * Todos los IDs/timestamps llegan como parámetro desde Kotlin
 * (`UUID.randomUUID()`, `AppClock`, ver [DurableTelemetryQueue]) — CERO
 * defaults de Room/SQLite, mismo espíritu que la regla "sin lógica en la DB"
 * del lado Go (`CLAUDE.md` de `msp-api`), aplicada aquí por determinismo de
 * tests y coherencia con el resto del repo.
 *
 * @property id UUID v4 generado en Kotlin al encolar — PK explícita, nunca autogen de Room.
 * @property type Nombre de [com.example.msp_app.core.telemetry.TelemetryEventType] (`SCREEN_VIEW`/`TAP`/`EVENT`/`ERROR`).
 *   Se persiste como `String` (no un TypeConverter de enum) siguiendo el patrón ya
 *   establecido en `:core:database` (columnas de estado/enum como texto plano,
 *   ver `CobranzaSyncStateEntity`) — sin sumar un mecanismo nuevo al repo.
 * @property name Identificador estático del evento (ya validado por el VO al construirse).
 * @property propsJson `props` del VO serializado (ver [TelemetryPropsCodec]) — siempre
 *   pares `String`/`String` estáticos, nunca PII (invariante impuesto aguas arriba por el VO).
 * @property occurredAt Instante de negocio del evento (`AppClock`, epoch millis) — cuándo
 *   ocurrió, NO cuándo se encoló.
 * @property state Ver [TelemetryEventState]; persistido como `String` (mismo motivo que [type]).
 * @property attemptCount Cuántas veces [DurableTelemetryQueue.drain] intentó entregarlo al sink.
 *   Solo se incrementa al FALLAR un intento (ver [TelemetryEventDao.markFailed]).
 * @property lastAttemptAt Epoch millis del último intento de entrega (`markUploading`/`markFailed`);
 *   `null` si nunca se intentó drenar (recién encolado).
 * @property nextAttemptAt Epoch millis a partir del cual el evento vuelve a ser elegible
 *   para drenado — implementa el backoff exponencial (spec §7.1). Al encolar, se
 *   inicializa en `createdAt` (elegible de inmediato). Columna NO listada explícitamente
 *   en el brief de Task 3 pero necesaria para que `markFailed(ids, nextAttemptAt)`
 *   (sí especificado por el brief) tenga dónde persistir su parámetro — decisión de
 *   esta tarea, documentada aquí en vez de inventada en silencio (mismo criterio que
 *   `TelemetryEvent.kt` usa para sus propias decisiones YAGNI).
 * @property createdAt Epoch millis en que la fila se insertó — la clave real de orden
 *   FIFO (índice compuesto con [state] abajo), no [occurredAt].
 */
@Entity(
    tableName = "telemetry_events",
    indices = [Index(value = ["state", "createdAt"])]
)
data class TelemetryEventEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val propsJson: String,
    val occurredAt: Long,
    val state: String,
    val attemptCount: Int,
    val lastAttemptAt: Long?,
    val nextAttemptAt: Long,
    val createdAt: Long
)
