package com.example.msp_app.core.telemetry.queue

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas directas del store tonto (sin política de reintento/backoff, eso
 * es [DurableTelemetryQueueTest]): orden FIFO crudo, semántica de cada
 * `mark*`, exención de `type=ERROR` en [TelemetryEventDao.deleteSent], y la
 * recuperación de `UPLOADING` colgado.
 */
class TelemetryEventDaoTest : RobolectricTestBase() {

    private lateinit var db: TelemetryDatabase
    private lateinit var dao: TelemetryEventDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TelemetryDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.telemetryEventDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        id: String,
        createdAt: Long,
        state: TelemetryEventState = TelemetryEventState.PENDING,
        type: String = "EVENT",
        attemptCount: Int = 0,
        nextAttemptAt: Long = createdAt
    ) = TelemetryEventEntity(
        id = id,
        type = type,
        name = "evento_$id",
        propsJson = "{}",
        occurredAt = createdAt,
        state = state.name,
        attemptCount = attemptCount,
        lastAttemptAt = null,
        nextAttemptAt = nextAttemptAt,
        createdAt = createdAt
    )

    @Test
    fun `insert persiste y nextBatch respeta orden de insercion (rowid)`() = runTest {
        dao.insert(entity("e1", createdAt = 100))
        dao.insert(entity("e2", createdAt = 200))
        dao.insert(entity("e3", createdAt = 300))

        val batch = dao.nextBatch(limit = 10)

        assertEquals(listOf("e1", "e2", "e3"), batch.map { it.id })
    }

    // Fix ronda 1 (Important 2): el orden de drenado es por ROWID (insercion),
    // NO por `createdAt`. Este test lo prueba de forma explicita: e1 se
    // inserta PRIMERO pero con el `createdAt` MAS GRANDE (mas "tarde" en
    // timestamp) — si `nextBatch` ordenara por `createdAt`, e1 quedaria de
    // ULTIMO; como ordena por rowid, e1 sale PRIMERO (fue insertado primero).
    @Test
    fun `nextBatch ordena por insercion, no por createdAt, aunque los timestamps esten invertidos`() =
        runTest {
            dao.insert(entity("e1", createdAt = 999))
            dao.insert(entity("e2", createdAt = 500))
            dao.insert(entity("e3", createdAt = 1))

            val batch = dao.nextBatch(limit = 10)

            assertEquals(listOf("e1", "e2", "e3"), batch.map { it.id })
        }

    // Fix ronda 1 (Important 2): el caso concreto que reporto el revisor —
    // 2+ eventos con el MISMO `createdAt` deben drenar en orden de insercion.
    @Test
    fun `nextBatch desempata por insercion cuando varios eventos comparten el mismo createdAt`() =
        runTest {
            val mismoTimestamp = 12345L
            dao.insert(entity("primero", createdAt = mismoTimestamp))
            dao.insert(entity("segundo", createdAt = mismoTimestamp))
            dao.insert(entity("tercero", createdAt = mismoTimestamp))

            val batch = dao.nextBatch(limit = 10)

            assertEquals(listOf("primero", "segundo", "tercero"), batch.map { it.id })
        }

    @Test
    fun `nextBatch respeta el limit`() = runTest {
        repeat(5) { i -> dao.insert(entity("e$i", createdAt = i.toLong())) }

        val batch = dao.nextBatch(limit = 2)

        assertEquals(2, batch.size)
        assertEquals(listOf("e0", "e1"), batch.map { it.id })
    }

    @Test
    fun `nextBatch no incluye eventos SENT ni UPLOADING`() = runTest {
        dao.insert(entity("sent", createdAt = 1, state = TelemetryEventState.SENT))
        dao.insert(entity("uploading", createdAt = 2, state = TelemetryEventState.UPLOADING))
        dao.insert(entity("pending", createdAt = 3, state = TelemetryEventState.PENDING))
        dao.insert(entity("failed", createdAt = 4, state = TelemetryEventState.FAILED))

        val batch = dao.nextBatch(limit = 10)

        assertEquals(setOf("pending", "failed"), batch.map { it.id }.toSet())
    }

    @Test
    fun `markUploading actualiza estado y lastAttemptAt`() = runTest {
        dao.insert(entity("e1", createdAt = 1))

        dao.markUploading(listOf("e1"), now = 999L)

        val row = dao.nextBatch(
            10
        ) // UPLOADING no aparece acá, se verifica vía recoverStuckUploading
        assertTrue("UPLOADING no debe estar en nextBatch", row.isEmpty())

        dao.recoverStuckUploading()
        val recovered = dao.nextBatch(10).single()
        assertEquals(TelemetryEventState.PENDING.name, recovered.state)
        assertEquals(999L, recovered.lastAttemptAt)
    }

    @Test
    fun `markSent saca el evento del nextBatch`() = runTest {
        dao.insert(entity("e1", createdAt = 1))

        dao.markSent(listOf("e1"))

        assertTrue(dao.nextBatch(10).isEmpty())
    }

    @Test
    fun `markFailed incrementa attemptCount y agenda nextAttemptAt`() = runTest {
        dao.insert(entity("e1", createdAt = 1))

        dao.markFailed(listOf("e1"), now = 500L, nextAttemptAt = 800L)

        val row = dao.nextBatch(10).single()
        assertEquals(TelemetryEventState.FAILED.name, row.state)
        assertEquals(1, row.attemptCount)
        assertEquals(500L, row.lastAttemptAt)
        assertEquals(800L, row.nextAttemptAt)

        // Un segundo fallo suma sobre el contador existente, no lo resetea.
        dao.markFailed(listOf("e1"), now = 900L, nextAttemptAt = 1500L)
        val second = dao.nextBatch(10).single()
        assertEquals(2, second.attemptCount)
    }

    @Test
    fun `pendingCount cuenta todo lo que no esta SENT`() = runTest {
        dao.insert(entity("a", createdAt = 1, state = TelemetryEventState.PENDING))
        dao.insert(entity("b", createdAt = 2, state = TelemetryEventState.FAILED))
        dao.insert(entity("c", createdAt = 3, state = TelemetryEventState.UPLOADING))
        dao.insert(entity("d", createdAt = 4, state = TelemetryEventState.SENT))

        assertEquals(3, dao.pendingCount())
    }

    @Test
    fun `deleteSent purga SENT viejos pero NUNCA type ERROR aunque envejezcan`() = runTest {
        dao.insert(
            entity("ruido_viejo", createdAt = 1, state = TelemetryEventState.SENT, type = "EVENT")
        )
        dao.insert(
            entity("error_viejo", createdAt = 1, state = TelemetryEventState.SENT, type = "ERROR")
        )
        dao.insert(
            entity(
                "ruido_reciente",
                createdAt = 1_000_000,
                state = TelemetryEventState.SENT,
                type = "TAP"
            )
        )
        dao.insert(
            entity("no_sent", createdAt = 1, state = TelemetryEventState.PENDING, type = "EVENT")
        )

        val deleted = dao.deleteSent(olderThan = 500_000L)

        assertEquals(1, deleted)
        val remainingIds = dao.nextBatch(10).map { it.id } // solo trae PENDING/FAILED
        assertTrue("no_sent debe seguir vivo (nunca fue SENT)", remainingIds.contains("no_sent"))
        // error_viejo (type=ERROR) y ruido_reciente (no cumple olderThan) sobreviven.
        assertEquals(3, countAllRows())
    }

    @Test
    fun `recoverStuckUploading vuelve PENDING solo las filas UPLOADING`() = runTest {
        dao.insert(entity("uploading1", createdAt = 1, state = TelemetryEventState.UPLOADING))
        dao.insert(entity("uploading2", createdAt = 2, state = TelemetryEventState.UPLOADING))
        dao.insert(entity("pending", createdAt = 3, state = TelemetryEventState.PENDING))
        dao.insert(entity("sent", createdAt = 4, state = TelemetryEventState.SENT))

        val recovered = dao.recoverStuckUploading()

        assertEquals(2, recovered)
        val drainable = dao.nextBatch(10).map { it.id }.toSet()
        assertEquals(setOf("uploading1", "uploading2", "pending"), drainable)
    }

    /** `pendingCount()`/`nextBatch()` deliberadamente no ven `SENT` — lectura cruda para contar TODAS las filas. */
    private fun countAllRows(): Int = db.openHelper.readableDatabase.query(
        "SELECT COUNT(*) FROM telemetry_events"
    ).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
}
