package com.example.msp_app.core.sync.visitas

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase.Companion.ERROR_CODE_STREAM_CAIDO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase.Companion.ERROR_CODE_STREAM_DESHABILITADO
import com.example.msp_app.core.sync.cobranza.UserContext
import com.example.msp_app.core.testing.RobolectricTestBase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The visitas SSE subscriber, driven against a real [MockWebServer].
 *
 * ## What this class is really guarding
 *
 * The task brief originally asked for one forward-compatibility property: *an
 * unknown event type does not break the stream.* A probe against the cobranza
 * subscriber showed that property is not enough — that client survives an
 * unrecognised event and then **acts on it anyway**, because it decides an
 * event's meaning from which socket delivered it rather than from the event's
 * name. It dispatched a `visitas_confirmadas` event as `(PAGOS, [])`, which in
 * production is a full cobranza cursor sync.
 *
 * So the bar here is both halves, asserted separately:
 *
 *  - the connection survives an unknown event, **and**
 *  - the client takes no action on it.
 *
 * A test that only checked the first would have passed against the defect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VisitasSseSubscriberTest : RobolectricTestBase() {

    private lateinit var server: MockWebServer
    private lateinit var testScope: CoroutineScope

    /** Counts reconciliation triggers — the ACTION whose absence most tests assert. */
    private val triggers = AtomicInteger(0)

    private val reportedCodes = java.util.Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @After
    fun teardown() {
        testScope.cancel()
        server.shutdown()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun sseResponse(body: String): MockResponse = MockResponse()
        .addHeader("Content-Type", "text/event-stream")
        .addHeader("Cache-Control", "no-cache")
        .setBody(body)

    private fun event(type: String, data: String = """{"ts":1}"""): String =
        "event: $type\ndata: $data\n\n"

    private fun buildSubscriber(
        onTrigger: suspend () -> Unit = { triggers.incrementAndGet() },
        latch: CountDownLatch? = null,
        zona: Int? = 21
    ): VisitasSseSubscriber {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val handler = HandleVisitsPushEventUseCase(
            triggerReconciliation = {
                onTrigger()
                latch?.countDown()
            },
            errorReporter = object : SyncErrorReporter {
                override fun report(code: String, message: String, props: Map<String, String>) {
                    reportedCodes += code
                    latch?.countDown()
                }
            }
        )
        return VisitasSseSubscriber(
            okHttpClient = client,
            baseUrl = server.url("/").toString(),
            userContextFlow = MutableStateFlow(
                zona?.let { UserContext(zona = it, fechaCargaInicial = null) }
            ).asStateFlow(),
            handler = handler,
            coroutineScope = testScope
        )
    }

    // ─── El evento hace su trabajo ───────────────────────────────────────────

    @Test
    fun `el evento de visitas dispara una reconciliacion`() {
        val latch = CountDownLatch(1)
        server.enqueue(sseResponse(event("visitas_confirmadas")))

        val subscriber = buildSubscriber(latch = latch)
        subscriber.start()
        val fired = latch.await(5, TimeUnit.SECONDS)
        subscriber.stop()

        assertTrue("recibir visitas_confirmadas debe disparar la reconciliación", fired)
        assertEquals(1, triggers.get())
    }

    @Test
    fun `el stream se abre contra la ruta de visitas, no la de cobranza`() {
        server.enqueue(sseResponse(""))

        val subscriber = buildSubscriber()
        subscriber.start()
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        subscriber.stop()

        assertEquals(
            "debe pedir su propia ruta — los teléfonos viejos abren otras dos y " +
                "por eso su exposición a este evento es cero",
            "/v2/visitas/sync/visitas/zona/21/stream",
            request?.path
        )
        assertEquals("text/event-stream", request?.getHeader("Accept"))
    }

    // ─── Compatibilidad hacia adelante: LAS DOS MITADES ──────────────────────

    /**
     * Mitad 1 — supervivencia: tras un evento desconocido, el siguiente evento
     * conocido en la MISMA conexión sigue llegando.
     *
     * Mitad 2 — inacción: el desconocido no disparó nada. Se prueba contando
     * los disparos: si el cliente actuara sobre cualquier evento (el defecto
     * medido en cobranza), habría DOS.
     */
    @Test
    fun `un tipo desconocido no rompe el stream y tampoco dispara nada`() {
        val latch = CountDownLatch(1)
        server.enqueue(
            sseResponse(
                event("un_evento_del_futuro", """{"ts":1,"ids":["no-soy-un-int"]}""") +
                    event("visitas_confirmadas")
            )
        )

        val subscriber = buildSubscriber(latch = latch)
        subscriber.start()
        val fired = latch.await(5, TimeUnit.SECONDS)
        // Margen para que un disparo extra indebido alcance a registrarse.
        Thread.sleep(300)
        subscriber.stop()

        assertTrue(
            "MITAD 1 — el evento conocido que viene DESPUÉS del desconocido debe " +
                "llegar: el stream sobrevivió",
            fired
        )
        assertEquals(
            "MITAD 2 — exactamente un disparo. Dos significarían que el cliente " +
                "actuó también sobre el evento desconocido, que es el defecto " +
                "medido en CobranzaSseSubscriber",
            1,
            triggers.get()
        )
    }

    @Test
    fun `un evento de cobranza en este stream no dispara nada`() {
        server.enqueue(sseResponse(event("pagos_changed", """{"ts":1,"ids":[7]}""")))

        val subscriber = buildSubscriber()
        subscriber.start()
        Thread.sleep(1_000)
        subscriber.stop()

        assertEquals(
            "el nombre manda, no el socket",
            0,
            triggers.get()
        )
    }

    @Test
    fun `un evento sin nombre no dispara nada`() {
        server.enqueue(sseResponse("data: {\"ts\":1}\n\n"))

        val subscriber = buildSubscriber()
        subscriber.start()
        Thread.sleep(1_000)
        subscriber.stop()

        assertEquals(0, triggers.get())
    }

    // ─── Norma de errores ────────────────────────────────────────────────────

    @Test
    fun `un 503 del servidor se reporta con su codigo y detiene el stream`() {
        val latch = CountDownLatch(1)
        repeat(3) {
            server.enqueue(
                MockResponse().setResponseCode(503)
                    .setBody("""{"errors":[{"message":"code=sse_deshabilitado"}]}""")
            )
        }

        val subscriber = buildSubscriber(latch = latch)
        subscriber.start()
        latch.await(5, TimeUnit.SECONDS)
        Thread.sleep(500)
        subscriber.stop()

        assertTrue(
            "el 503 debe reportarse con su propio código, distinto de una caída",
            reportedCodes.contains(ERROR_CODE_STREAM_DESHABILITADO)
        )
        assertFalse(
            "un flag apagado a propósito no es una caída",
            reportedCodes.contains(ERROR_CODE_STREAM_CAIDO)
        )
        assertTrue(
            "no debe reintentar contra un endpoint apagado a propósito",
            server.requestCount <= 2
        )
    }

    @Test
    fun `una caida del stream se reporta con su codigo nombrado`() {
        val latch = CountDownLatch(1)
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val subscriber = buildSubscriber(latch = latch)
        subscriber.start()
        latch.await(5, TimeUnit.SECONDS)
        subscriber.stop()

        assertTrue(
            "un stream que muere en silencio es invisible por construcción",
            reportedCodes.contains(ERROR_CODE_STREAM_CAIDO)
        )
    }

    // ─── Ciclo de vida ───────────────────────────────────────────────────────

    @Test
    fun `sin zona no se abre ningun stream`() {
        val subscriber = buildSubscriber(zona = null)
        subscriber.start()
        Thread.sleep(500)
        subscriber.stop()

        assertEquals(
            "la ruta exige zona_id positivo; sin zona el teléfono se queda con " +
                "los otros tres disparadores",
            0,
            server.requestCount
        )
    }

    @Test
    fun `stop es idempotente y no lanza`() {
        server.enqueue(sseResponse(""))
        val subscriber = buildSubscriber()
        subscriber.start()
        subscriber.stop()
        subscriber.stop()
    }

    @Test
    fun `start es idempotente`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = sseResponse("")
        }
        val subscriber = buildSubscriber()
        subscriber.start()
        subscriber.start()
        Thread.sleep(500)
        subscriber.stop()

        assertEquals("dos start no deben abrir dos streams", 1, server.requestCount)
    }

    @Test
    fun `el backoff crece y topa en 30s`() {
        val subscriber = buildSubscriber()
        assertEquals(1_000L, subscriber.backoffMillis(0))
        assertEquals(2_000L, subscriber.backoffMillis(1))
        assertEquals(16_000L, subscriber.backoffMillis(4))
        assertEquals(30_000L, subscriber.backoffMillis(5))
        assertEquals(30_000L, subscriber.backoffMillis(99))
    }
}
