package com.example.msp_app.core.telemetry.adapter

import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.telemetry.queue.AlwaysFailingSink
import com.example.msp_app.core.telemetry.queue.DurableTelemetryQueue
import com.example.msp_app.core.telemetry.queue.InMemoryTelemetryEventDao
import com.example.msp_app.core.telemetry.queue.RecordingSink
import com.example.msp_app.core.telemetry.queue.TelemetryEventState
import com.example.msp_app.core.telemetry.queue.TelemetryPropsCodec
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cada método del puerto [com.example.msp_app.core.telemetry.Telemetry] debe
 * encolar el [com.example.msp_app.core.telemetry.TelemetryEvent] correcto
 * (tipo/nombre/props) en la cola durable (T3) — brief Task 4. Se inspecciona
 * directamente el [InMemoryTelemetryEventDao] (fake ya existente en `queue`,
 * reusado acá) en vez de pasar por `drain()`, para no consumir la fila antes
 * de poder afirmar sobre ella.
 *
 * Robolectric (no JVM puro): [com.example.msp_app.core.telemetry.queue.TelemetryPropsCodec]
 * serializa `props` con `org.json.JSONObject` (stub de Android que lanza
 * "not mocked" fuera de Robolectric) — mismo motivo que `DurableTelemetryQueueTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DurableTelemetryTest : RobolectricTestBase() {

    private val clock = FakeClock.at("2026-08-09T12:00:00Z")
    private val dao = InMemoryTelemetryEventDao()
    private val queue = DurableTelemetryQueue(dao, clock)

    @Test
    fun `screenView encola un TelemetryEvent SCREEN_VIEW con name igual a la pantalla`() = runTest {
        val telemetry = DurableTelemetry(queue, RecordingSink(), clock, enqueueScope = this)

        telemetry.screenView("reporte")
        advanceUntilIdle()

        val stored = dao.nextBatch(10).single()
        assertEquals(TelemetryEventType.SCREEN_VIEW.name, stored.type)
        assertEquals("reporte", stored.name)
        assertEquals(emptyMap<String, String>(), TelemetryPropsCodec.decode(stored.propsJson))
    }

    @Test
    fun `tap encola un TelemetryEvent TAP con name igual al elemento y screen en props`() =
        runTest {
            val telemetry = DurableTelemetry(queue, RecordingSink(), clock, enqueueScope = this)

            telemetry.tap("cobranza_detalle", "boton_confirmar")
            advanceUntilIdle()

            val stored = dao.nextBatch(10).single()
            assertEquals(TelemetryEventType.TAP.name, stored.type)
            assertEquals("boton_confirmar", stored.name)
            assertEquals(
                mapOf("screen" to "cobranza_detalle"),
                TelemetryPropsCodec.decode(stored.propsJson)
            )
        }

    @Test
    fun `event encola un TelemetryEvent EVENT con name y props tal cual`() = runTest {
        val telemetry = DurableTelemetry(queue, RecordingSink(), clock, enqueueScope = this)

        telemetry.event("pago_registrado", mapOf("resultado" to "exito"))
        advanceUntilIdle()

        val stored = dao.nextBatch(10).single()
        assertEquals(TelemetryEventType.EVENT.name, stored.type)
        assertEquals("pago_registrado", stored.name)
        assertEquals(
            mapOf("resultado" to "exito"),
            TelemetryPropsCodec.decode(stored.propsJson)
        )
    }

    @Test
    fun `error encola un TelemetryEvent ERROR con code como name y message en props`() = runTest {
        val telemetry = DurableTelemetry(queue, RecordingSink(), clock, enqueueScope = this)

        telemetry.error("red_timeout", "conexion rechazada", mapOf("intentos" to "3"))

        // Sin advanceUntilIdle(): error() es SINCRONO (runBlocking), por lo
        // que la fila ya debe existir apenas retorna la llamada.
        val stored = dao.nextBatch(10).single()
        assertEquals(TelemetryEventType.ERROR.name, stored.type)
        assertEquals("red_timeout", stored.name)
        assertEquals(
            mapOf("intentos" to "3", "message" to "conexion rechazada"),
            TelemetryPropsCodec.decode(stored.propsJson)
        )
    }

    @Test
    fun `error es sincrono, no requiere avanzar ningun scheduler para aparecer encolado`() {
        // Deliberadamente FUERA de runTest/advanceUntilIdle: si error() fuera
        // asincrono (como screenView/tap/event), esta fila NO existiria todavia
        // al momento del assert. El unico `runBlocking` acá es del propio test
        // (para poder leer el fake suspend `dao.nextBatch`), no de `error()`.
        val telemetry = DurableTelemetry(queue, RecordingSink(), clock)

        telemetry.error("fallo_critico", "detalle tecnico")

        val stored = runBlocking { dao.nextBatch(10) }
        assertEquals(1, stored.size)
    }

    @Test
    fun `drain con StubTelemetrySink marca el evento SENT`() = runTest {
        val telemetry = DurableTelemetry(queue, StubTelemetrySink(), clock, enqueueScope = this)
        telemetry.event("sync_ok")
        advanceUntilIdle()
        assertEquals(TelemetryEventState.PENDING.name, dao.nextBatch(10).single().state)

        val delivered = telemetry.drain()

        assertEquals(1, delivered)
        assertTrue(
            "un evento SENT no debe volver a aparecer en nextBatch",
            dao.nextBatch(10).isEmpty()
        )
    }

    @Test
    fun `drain propaga el conteo entregado de la cola subyacente`() = runTest {
        val sink = RecordingSink()
        val telemetry = DurableTelemetry(queue, sink, clock, enqueueScope = this)
        telemetry.event("e1")
        telemetry.event("e2")
        advanceUntilIdle()

        val delivered = telemetry.drain()

        assertEquals(2, delivered)
        assertEquals(listOf("e1", "e2"), sink.sent.map { it.name })
    }

    @Test
    fun `drain con un sink caido no propaga excepcion, hereda el best-effort de la cola`() =
        runTest {
            val telemetry = DurableTelemetry(queue, AlwaysFailingSink(), clock, enqueueScope = this)
            telemetry.event("ruido")
            advanceUntilIdle()

            val delivered = telemetry.drain()

            assertEquals(0, delivered)
        }
}
