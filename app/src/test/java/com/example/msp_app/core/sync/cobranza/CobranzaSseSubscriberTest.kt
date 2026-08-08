package com.example.msp_app.core.sync.cobranza

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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias del [CobranzaSseSubscriber] usando [MockWebServer].
 *
 * Los tests que validan callbacks OkHttp (onEvent, onFailure) usan un scope
 * con [Dispatchers.Default] (tiempo real) y [CountDownLatch] porque
 * EventSource corre en threads propios de OkHttp y el debounce de 100ms
 * debe disparar sin control manual del scheduler.
 *
 * El test de backoff cap opera sin I/O y verifica la función pura
 * [CobranzaSseSubscriber.backoffMillis] directamente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CobranzaSseSubscriberTest : RobolectricTestBase() {

    private lateinit var server: MockWebServer

    /** Scope real para tests que involucran OkHttp + debounce de coroutine. */
    private lateinit var testScope: CoroutineScope

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

    /** Cuerpo SSE con líneas de un evento. */
    private fun sseBody(vararg lines: String): String = lines.joinToString("\n") + "\n\n"

    /** Respuesta SSE válida — MockWebServer cierra la conexión al escribir el body. */
    private fun sseResponse(body: String): MockResponse = MockResponse()
        .addHeader("Content-Type", "text/event-stream")
        .addHeader("Cache-Control", "no-cache")
        .setBody(body)

    /** Respuesta 503 que simula el feature flag apagado. */
    private fun featureFlagOffResponse(): MockResponse = MockResponse()
        .setResponseCode(503)
        .addHeader("Content-Type", "application/json")
        .setBody("""{"error":"servicio de notificaciones no disponible"}""")

    /**
     * Construye un [CobranzaSseSubscriber] apuntando al [MockWebServer].
     * El OkHttpClient no tiene timeout de lectura (igual que producción).
     */
    private fun buildSubscriber(
        userContext: UserContext? = UserContext(zona = 21, fechaCargaInicial = null),
        onEvent: suspend (SseKind, List<Int>) -> Unit = { _, _ -> },
        contextFlow: MutableStateFlow<UserContext?> = MutableStateFlow(userContext),
        scope: CoroutineScope = testScope
    ): CobranzaSseSubscriber {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        return CobranzaSseSubscriber(
            okHttpClient = client,
            baseUrl = server.url("/").toString(),
            userContextFlow = contextFlow.asStateFlow(),
            onEvent = onEvent,
            coroutineScope = scope
        )
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    /**
     * 1. El servidor envía `event: pagos_changed\ndata: {}\n\n`.
     *    [onEvent] debe invocarse dentro de 3s (debounce 100ms + latencia OkHttp).
     */
    @Test
    fun eventTriggersSyncNow() {
        val latch = CountDownLatch(1)
        val pagoEvent = sseBody("event: pagos_changed", "data: {}")

        server.enqueue(sseResponse(pagoEvent))
        server.enqueue(sseResponse("")) // saldos: sin eventos

        val subscriber = buildSubscriber(onEvent = { _, _ -> latch.countDown() })
        subscriber.start()

        val fired = latch.await(3, TimeUnit.SECONDS)
        subscriber.stop()

        assertTrue("onEvent debe haberse llamado al recibir un evento SSE de pagos", fired)
    }

    /**
     * 2. El servidor dispara 5 eventos en un solo body; [onEvent] debe
     *    llamarse exactamente una vez (debounce coalescing).
     */
    @Test
    fun coalescedEventsDebounced() {
        val called = AtomicInteger(0)
        val firstLatch = CountDownLatch(1)

        val burst = buildString {
            repeat(5) { append("event: pagos_changed\ndata: {}\n\n") }
        }
        server.enqueue(sseResponse(burst))
        server.enqueue(sseResponse("")) // saldos: sin eventos

        val subscriber = buildSubscriber(
            onEvent = { _, _ ->
                called.incrementAndGet()
                firstLatch.countDown()
            }
        )
        subscriber.start()

        // Esperar la primera invocación (máximo 3s).
        firstLatch.await(3, TimeUnit.SECONDS)
        // Dar un margen adicional para que lleguen posibles llamadas extras.
        Thread.sleep(CobranzaSseSubscriber.DEBOUNCE_MS * 3)
        subscriber.stop()

        assertEquals(
            "5 eventos en ráfaga deben coalescer en exactamente 1 llamada",
            1,
            called.get()
        )
    }

    /**
     * 3. El servidor responde 503 (feature flag off). No debe haber
     *    reconexión — contamos requests durante 3s; deben ser <= 2.
     */
    @Test
    fun featureFlagOff_returns503_doesNotReconnect() {
        server.enqueue(featureFlagOffResponse())
        server.enqueue(featureFlagOffResponse())

        val subscriber = buildSubscriber()
        subscriber.start()

        // Esperar a que OkHttp procese los 503.
        Thread.sleep(3_000)

        val requestCount = server.requestCount
        subscriber.stop()

        assertTrue(
            "Después de 503 no debe haber reconexión — requests esperados <= 2, recibidos=$requestCount",
            requestCount <= 2
        )
    }

    /**
     * 4. El servidor devuelve 500 la primera vez y un evento SSE la segunda.
     *    [onEvent] debe llamarse después de la reconexión (backoff base = 1s).
     */
    @Test
    fun networkFailureReconnectsWithBackoff() {
        val latch = CountDownLatch(1)

        // Primera ronda: 500 en ambos endpoints.
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        // Segunda ronda: éxito con evento en pagos.
        server.enqueue(sseResponse(sseBody("event: pagos_changed", "data: {}")))
        server.enqueue(sseResponse(""))

        val subscriber = buildSubscriber(onEvent = { _, _ -> latch.countDown() })
        subscriber.start()

        // Backoff base = 1s; toleramos hasta 5s para latencia del hilo.
        val fired = latch.await(5, TimeUnit.SECONDS)
        subscriber.stop()

        assertTrue("onEvent debe llamarse después de reconexión exitosa post-500", fired)
    }

    /**
     * 5. Verificar que el backoff siga 1s, 2s, 4s, 8s, 16s, 30s, 30s, 30s.
     *    Se prueba [CobranzaSseSubscriber.backoffMillis] directamente.
     */
    @Test
    fun backoffCapsAt30s() {
        val subscriber = CobranzaSseSubscriber(
            okHttpClient = OkHttpClient(),
            baseUrl = "http://localhost/",
            userContextFlow = MutableStateFlow<UserContext?>(null).asStateFlow(),
            onEvent = { _, _ -> },
            coroutineScope = TestScope(StandardTestDispatcher())
        )

        val expected = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000, 30_000)
        for (attempt in expected.indices) {
            assertEquals(
                "backoffMillis(attempt=$attempt) debe ser ${expected[attempt]}ms",
                expected[attempt],
                subscriber.backoffMillis(attempt)
            )
        }
    }

    /**
     * 6. start() → recibir un evento → stop(). Después de stop() no deben
     *    abrirse nuevas conexiones en 500ms.
     */
    @Test
    fun stopClosesStreams() {
        val latch = CountDownLatch(1)
        server.enqueue(sseResponse(sseBody("event: pagos_changed", "data: {}")))
        server.enqueue(sseResponse(""))

        val subscriber = buildSubscriber(onEvent = { _, _ -> latch.countDown() })
        subscriber.start()

        // Esperar el primer evento para confirmar la conexión.
        latch.await(3, TimeUnit.SECONDS)
        subscriber.stop()

        val requestsAfterStop = server.requestCount
        Thread.sleep(500)

        assertEquals(
            "No debe haber requests adicionales después de stop()",
            requestsAfterStop,
            server.requestCount
        )
    }

    /**
     * 7. Zona cambia de 21 a 42. Los streams nuevos deben apuntar a
     *    `/zona/42/stream`.
     */
    @Test
    fun zoneChangeReopensStreams() {
        // Zona 21: dos streams que cierran sin eventos.
        server.enqueue(sseResponse(""))
        server.enqueue(sseResponse(""))
        // Zona 42: dos streams nuevos.
        server.enqueue(sseResponse(sseBody("event: pagos_changed", "data: {}")))
        server.enqueue(sseResponse(""))

        val contextFlow = MutableStateFlow<UserContext?>(
            UserContext(zona = 21, fechaCargaInicial = null)
        )
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val subscriber = CobranzaSseSubscriber(
            okHttpClient = client,
            baseUrl = server.url("/").toString(),
            userContextFlow = contextFlow.asStateFlow(),
            onEvent = { _, _ -> },
            coroutineScope = testScope
        )

        subscriber.start()
        // Dar tiempo a que los streams de zona 21 se abran y cierren.
        Thread.sleep(400)

        // Cambiar la zona.
        runBlocking { contextFlow.emit(UserContext(zona = 42, fechaCargaInicial = null)) }
        // Dar tiempo a que los nuevos streams se establezcan.
        Thread.sleep(600)

        // Recolectar todos los paths de requests ya completados.
        val paths = mutableListOf<String>()
        while (true) {
            val req = server.takeRequest(50, TimeUnit.MILLISECONDS) ?: break
            paths.add(req.path ?: "")
        }
        subscriber.stop()

        val zona42Paths = paths.filter { it.contains("/zona/42/") }
        assertTrue(
            "Después del cambio de zona deben abrirse streams para zona=42 — paths=$paths",
            zona42Paths.isNotEmpty()
        )
    }

    /**
     * 8. El endpoint de saldos envía `event: saldos_changed\ndata: {}\n\n`.
     *    [onEvent] debe invocarse.
     */
    @Test
    fun saldosEventAlsoTriggersSync() {
        val latch = CountDownLatch(1)

        // Pagos: sin eventos. Saldos: con evento.
        server.enqueue(sseResponse(""))
        val saldosEvent = sseBody("event: saldos_changed", "data: {}")
        server.enqueue(sseResponse(saldosEvent))

        val subscriber = buildSubscriber(onEvent = { _, _ -> latch.countDown() })
        subscriber.start()

        val fired = latch.await(3, TimeUnit.SECONDS)
        subscriber.stop()

        assertTrue("Un evento en el stream de saldos debe disparar onEvent", fired)
    }

    // ─── parseServerTsAndIds ─────────────────────────────────────────────────

    /** Fixture para tests puramente unitarios de parseServerTsAndIds. */
    private fun stubSubscriber(): CobranzaSseSubscriber = buildSubscriber()

    /**
     * 9. `{"ts":1234567890,"ids":[1,2,3]}` → ts=1234567890, ids=[1,2,3].
     */
    @Test
    fun parseServerTsAndIds_fullPayload() {
        val sub = stubSubscriber()
        val (ts, ids) = sub.parseServerTsAndIds("""{"ts":1234567890,"ids":[1,2,3]}""")
        assertEquals(1234567890L, ts)
        assertEquals(listOf(1, 2, 3), ids)
    }

    /**
     * 10. `{}` → ts=null, ids=null (campo ausente).
     */
    @Test
    fun parseServerTsAndIds_emptyPayload() {
        val sub = stubSubscriber()
        val (ts, ids) = sub.parseServerTsAndIds("{}")
        assertEquals(null, ts)
        assertEquals(null, ids)
    }

    /**
     * 11. `{"ts":999,"ids":[]}` → ids=emptyList() (campo presente pero vacío).
     */
    @Test
    fun parseServerTsAndIds_emptyIdsList() {
        val sub = stubSubscriber()
        val (ts, ids) = sub.parseServerTsAndIds("""{"ts":999,"ids":[]}""")
        assertEquals(999L, ts)
        assertEquals(emptyList<Int>(), ids)
    }

    /**
     * 12. Solo `ids`, sin `ts` → ts=null, ids=[10,20].
     */
    @Test
    fun parseServerTsAndIds_onlyIds() {
        val sub = stubSubscriber()
        val (ts, ids) = sub.parseServerTsAndIds("""{"ids":[10,20]}""")
        assertEquals(null, ts)
        assertEquals(listOf(10, 20), ids)
    }
}
