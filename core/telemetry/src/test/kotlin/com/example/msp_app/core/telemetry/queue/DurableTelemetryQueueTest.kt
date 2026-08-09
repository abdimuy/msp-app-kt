package com.example.msp_app.core.telemetry.queue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.telemetry.TelemetryEvent
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val DB_FILE_NAME = "durable-telemetry-queue-test.db"

/**
 * Robustez SUPREMA de la cola durable (spec §7.1, brief Task 3). Los 3
 * invariantes duros — durable, FIFO, nunca tira errores — cada uno con AL
 * MENOS una prueba que lo prueba de forma directa, más los casos borde de
 * ack-based recovery y concurrencia que exige el brief.
 */
class DurableTelemetryQueueTest : RobolectricTestBase() {

    private lateinit var dbFile: File
    private lateinit var db: TelemetryDatabase
    private lateinit var dao: TelemetryEventDao
    private val clock = FakeClock.at("2026-08-09T12:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File(context.cacheDir, DB_FILE_NAME)
        dbFile.delete()
        openFileBackedDb()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        dbFile.delete()
    }

    private fun openFileBackedDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TelemetryDatabase.buildDatabase(context, dbFile.path)
            .allowMainThreadQueries()
            .build()
        dao = db.telemetryEventDao()
    }

    private fun someEvent(
        name: String = "pantalla_vista",
        type: TelemetryEventType = TelemetryEventType.EVENT
    ) = TelemetryEvent(type = type, name = name, occurredAt = clock.now())

    // --- 1. Encolar sobrevive: cerrar y reabrir la DB (disco-primero) ---

    @Test
    fun `enqueue sobrevive cerrar y reabrir la DB por la ruta de produccion`() = runTest {
        val queue = DurableTelemetryQueue(dao, clock)
        queue.enqueue(someEvent("evento_1"))
        queue.enqueue(someEvent("evento_2"))
        queue.enqueue(someEvent("evento_3"))

        db.close()
        assertTrue("el archivo de la base debe persistir tras el close", dbFile.exists())

        openFileBackedDb()
        val reopenedQueue = DurableTelemetryQueue(dao, clock)

        assertEquals(3, reopenedQueue.pendingCount())
        val survivors = dao.nextBatch(10)
        assertEquals(3, survivors.size)
        assertTrue(survivors.all { it.state == TelemetryEventState.PENDING.name })
        assertEquals(setOf("evento_1", "evento_2", "evento_3"), survivors.map { it.name }.toSet())
    }

    // --- 2. Drena en orden FIFO ---

    @Test
    fun `drain entrega al sink en el mismo orden en que se encolo`() = runTest {
        val queue = DurableTelemetryQueue(dao, clock)
        queue.enqueue(someEvent("e1"))
        clock.advanceSeconds(1)
        queue.enqueue(someEvent("e2"))
        clock.advanceSeconds(1)
        queue.enqueue(someEvent("e3"))
        val sink = RecordingSink()

        val delivered = queue.drain(sink)

        assertEquals(3, delivered)
        assertEquals(listOf("e1", "e2", "e3"), sink.sent.map { it.name })
    }

    @Test
    fun `los eventos entregados quedan SENT`() = runTest {
        val queue = DurableTelemetryQueue(dao, clock)
        queue.enqueue(someEvent("e1"))
        val sink = RecordingSink()

        queue.drain(sink)

        val row = dao.nextBatch(10)
        assertTrue("un evento SENT no debe volver a aparecer en nextBatch", row.isEmpty())
        assertEquals(0, queue.pendingCount())
    }

    // --- drain remueve las filas drenadas: no hay doble envio ---

    @Test
    fun `un segundo drain no vuelve a entregar eventos ya SENT`() = runTest {
        val queue = DurableTelemetryQueue(dao, clock)
        queue.enqueue(someEvent("e1"))
        val sink = RecordingSink()

        queue.drain(sink)
        val secondDelivered = queue.drain(sink)

        assertEquals(0, secondDelivered)
        assertEquals(1, sink.callCount.get())
    }

    // --- 3. Falla de red -> FAILED + reintento con backoff ---

    @Test
    fun `un sink que falla marca FAILED, incrementa attemptCount y agenda backoff`() = runTest {
        val queue = DurableTelemetryQueue(dao, clock)
        queue.enqueue(someEvent("e1"))
        val failingSink = AlwaysFailingSink()

        val delivered = queue.drain(failingSink)

        assertEquals(0, delivered)
        val row = dao.nextBatch(10).single()
        assertEquals(TelemetryEventState.FAILED.name, row.state)
        assertEquals(1, row.attemptCount)
        assertTrue(
            "el backoff debe agendar el proximo intento en el futuro",
            row.nextAttemptAt > clock.now().toEpochMilli()
        )
        assertEquals(1, queue.pendingCount()) // FAILED sigue contando como pendiente, no se perdio

        // Re-drenar de inmediato (sin que pase el backoff) NO debe reintentar todavia.
        val immediateRetry = queue.drain(failingSink)
        assertEquals(0, immediateRetry)
    }

    @Test
    fun `re-drenar despues de que pasa el backoff reintenta el evento fallido`() = runTest {
        val queue = DurableTelemetryQueue(dao, clock)
        queue.enqueue(someEvent("e1"))
        val flakySink = RecordingSink(failFor = mapOf("e1" to 1))

        val firstAttempt = queue.drain(flakySink)
        assertEquals(0, firstAttempt)

        // Backoff base = 30s tras el primer fallo; avanzamos el reloj de sobra.
        clock.advanceMinutes(1)
        val secondAttempt = queue.drain(flakySink)

        assertEquals(1, secondAttempt)
        assertEquals(listOf("e1"), flakySink.sent.map { it.name })
        assertEquals(2, flakySink.callCount.get())
    }

    @Test
    fun `eventos type ERROR se reintentan indefinidamente, sin abandonarse por maxAttempts`() =
        runTest {
            val queue = DurableTelemetryQueue(dao, clock)
            queue.enqueue(
                TelemetryEvent(
                    type = TelemetryEventType.ERROR,
                    name = "fallo_critico",
                    occurredAt = clock.now()
                )
            )
            val failingSink = AlwaysFailingSink()

            // maxAttempts=1: para un evento no-ERROR esto lo abandonaria (marca SENT).
            repeat(3) {
                queue.drain(failingSink, maxAttempts = 1)
                clock.advanceMinutes(20) // de sobra para superar el tope de backoff (15 min)
            }

            val row = dao.nextBatch(10).single()
            assertEquals(TelemetryEventState.FAILED.name, row.state)
            assertEquals(3, row.attemptCount)
        }

    @Test
    fun `ruido no-ERROR se abandona tras maxAttempts y no bloquea el resto de la cola`() = runTest {
        val queue = DurableTelemetryQueue(dao, clock)
        queue.enqueue(someEvent("ruido_terco", type = TelemetryEventType.EVENT))
        val failingSink = AlwaysFailingSink()

        queue.drain(failingSink, maxAttempts = 1)
        clock.advanceMinutes(20)

        // Tras agotar maxAttempts=1 en el primer intento, se abandona (marca SENT):
        // ya no vuelve a aparecer en nextBatch ni en pendingCount.
        assertEquals(0, queue.pendingCount())
        assertTrue(dao.nextBatch(10).isEmpty())
    }

    // --- 5. Ack-based: recuperacion de UPLOADING colgado ---

    @Test
    fun `un evento marcado UPLOADING cuando el proceso muere se recupera y drena en el siguiente ciclo`() =
        runTest {
            val queue = DurableTelemetryQueue(dao, clock)
            queue.enqueue(someEvent("e1"))

            // Simula: un `drain` anterior marco UPLOADING pero el proceso murio antes
            // de recibir el ack del sink (ni SENT ni FAILED se escribieron).
            val pending = dao.nextBatch(10).single()
            dao.markUploading(listOf(pending.id), now = clock.now().toEpochMilli())
            assertTrue("UPLOADING no debe ser elegible directamente", dao.nextBatch(10).isEmpty())

            val sink = RecordingSink()
            val delivered = queue.drain(sink)

            assertEquals(1, delivered)
            assertEquals(listOf("e1"), sink.sent.map { it.name })
            assertEquals(1, sink.callCount.get()) // exactamente una entrega, no duplicada
        }

    @Test
    fun `un evento UPLOADING recuperado que reaparece SENT no se re-envia jamas`() = runTest {
        val queue = DurableTelemetryQueue(dao, clock)
        queue.enqueue(someEvent("e1"))
        val sink = RecordingSink()

        // Primer drain: entrega real, queda SENT (ack correcto).
        queue.drain(sink)

        // Un proceso posterior no debe encontrar nada elegible ni recuperar
        // esta fila (ya esta SENT, no UPLOADING) — el "reabrir" no la revive.
        val recovered = dao.recoverStuckUploading()
        assertEquals(0, recovered)
        assertEquals(0, queue.drain(sink))
        assertEquals(1, sink.callCount.get())
    }

    // --- 6. Concurrencia: encolar desde varias corrutinas no corrompe la cola ---

    @Test
    fun `encolar concurrentemente desde varias corrutinas no pierde ni duplica eventos`() =
        runTest {
            val queue = DurableTelemetryQueue(dao, clock)
            val total = 25

            coroutineScope {
                (1..total).map { i ->
                    async { queue.enqueue(someEvent("concurrente_$i")) }
                }.awaitAll()
            }

            assertEquals(total, queue.pendingCount())
            val ids = dao.nextBatch(total).map { it.id }
            assertEquals(total, ids.toSet().size) // ids UUID unicos, sin colision
        }

    // --- Drenar cola vacia es no-op ---

    @Test
    fun `drenar una cola vacia es un no-op, no llama al sink`() = runTest {
        val queue = DurableTelemetryQueue(dao, clock)
        val sink = RecordingSink()

        val delivered = queue.drain(sink)

        assertEquals(0, delivered)
        assertEquals(0, sink.callCount.get())
    }

    // --- 4. Nunca tira errores: enqueue con un DAO que siempre falla ---

    @Test
    fun `enqueue con un DAO que revienta no propaga la excepcion al caller`() = runTest {
        val failingDao = FailingTelemetryEventDao()
        val queue = DurableTelemetryQueue(failingDao, clock)

        // Si esto lanzara, el test fallaria con la excepcion sin llegar al assert.
        queue.enqueue(someEvent("e1"))
    }

    @Test
    fun `drain con un DAO que revienta no propaga la excepcion al caller y retorna 0`() = runTest {
        val failingDao = FailingTelemetryEventDao()
        val queue = DurableTelemetryQueue(failingDao, clock)
        val sink = RecordingSink()

        val delivered = queue.drain(sink)

        assertEquals(0, delivered)
        assertEquals(0, sink.callCount.get())
    }

    @Test
    fun `pendingCount con un DAO que revienta no propaga y retorna 0`() = runTest {
        val failingDao = FailingTelemetryEventDao()
        val queue = DurableTelemetryQueue(failingDao, clock)

        assertEquals(0, queue.pendingCount())
    }

    @Test
    fun `purgeSentNoise con un DAO que revienta no propaga`() = runTest {
        val failingDao = FailingTelemetryEventDao()
        val queue = DurableTelemetryQueue(failingDao, clock)

        queue.purgeSentNoise(clock.now())
    }

    @Test
    fun `un sink que revienta a mitad de un lote no tumba el drain, solo marca ese evento FAILED`() =
        runTest {
            val queue = DurableTelemetryQueue(dao, clock)
            queue.enqueue(someEvent("ok_1"))
            queue.enqueue(someEvent("falla"))
            clock.advanceSeconds(1)
            queue.enqueue(someEvent("ok_2"))
            val sink = RecordingSink(failFor = mapOf("falla" to Int.MAX_VALUE))

            val delivered = queue.drain(sink)

            assertEquals(2, delivered)
            assertEquals(listOf("ok_1", "ok_2"), sink.sent.map { it.name })
            assertFalse(queue.pendingCount() == 0) // "falla" sigue pendiente (FAILED, con backoff)
        }

    // --- markSent/markFailed del DAO tambien deben poder reventar sin propagar ---

    @Test
    fun `si el DAO revienta al marcar SENT tras una entrega exitosa, drain no propaga`() = runTest {
        val queue = DurableTelemetryQueue(dao, clock)
        queue.enqueue(someEvent("e1"))
        // dao real ya inserto el evento; ahora se reemplaza por uno que revienta
        // en escritura para simular un fallo de disco justo al confirmar el ack.
        val explodingOnWrite = object : TelemetryEventDao by dao {
            override suspend fun markSent(ids: List<String>): Nothing =
                error("disco lleno al confirmar ack")
        }
        val queueWithExplodingWrite = DurableTelemetryQueue(explodingOnWrite, clock)
        val sink = RecordingSink()

        val delivered = queueWithExplodingWrite.drain(sink)

        assertEquals(0, delivered) // el try/catch externo de drain() aborta el resto del lote
        assertEquals(
            1,
            sink.callCount.get()
        ) // el sink SI fue invocado antes del fallo de escritura

        // El proceso "revive" con el dao real: recoverStuckUploading() reclama
        // la fila que quedo en UPLOADING (nunca llego a marcarse SENT ni FAILED)
        // y la vuelve a drenar sin explotar ni perderla.
        val secondDelivered = queue.drain(sink)
        assertEquals(1, secondDelivered)
        assertEquals(2, sink.callCount.get())
    }
}
