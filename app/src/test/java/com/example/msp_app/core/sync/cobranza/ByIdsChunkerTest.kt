package com.example.msp_app.core.sync.cobranza

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ByIdsChunkerTest {

    @Before
    fun resetFlag() {
        // Cada test parte del estado limpio del flag de disponibilidad.
        ByIdsChunker.byIdsAvailable.set(true)
    }

    // ─── Partición correcta ──────────────────────────────────────────────────

    /**
     * Lista vacía → ninguna llamada a fetch, resultado vacío.
     */
    @Test
    fun emptyListProducesNoCalls() = runTest {
        val calls = AtomicInteger(0)
        val result = ByIdsChunker.fetchInChunks<Int>(emptyList()) { _ ->
            calls.incrementAndGet()
            emptyList()
        }
        assertEquals(0, calls.get())
        assertTrue(result.isEmpty())
    }

    /**
     * 1 ID → exactamente 1 chunk, 1 llamada.
     */
    @Test
    fun singleIdProducesOneChunk() = runTest {
        val calls = AtomicInteger(0)
        val result = ByIdsChunker.fetchInChunks(listOf(42)) { csv ->
            calls.incrementAndGet()
            listOf(csv.toInt())
        }
        assertEquals(1, calls.get())
        assertEquals(listOf(42), result)
    }

    /**
     * Exactamente 500 IDs → 1 chunk (no se parte en dos).
     */
    @Test
    fun exactly500IdsProducesOneChunk() = runTest {
        val ids = (1..500).toList()
        val calls = AtomicInteger(0)
        ByIdsChunker.fetchInChunks<Int>(ids) { _ ->
            calls.incrementAndGet()
            emptyList()
        }
        assertEquals(1, calls.get())
    }

    /**
     * 501 IDs → 2 chunks (500 + 1).
     */
    @Test
    fun fiveHundredOnePlusIdsProducesTwoChunks() = runTest {
        val ids = (1..501).toList()
        val calls = AtomicInteger(0)
        ByIdsChunker.fetchInChunks<Int>(ids) { _ ->
            calls.incrementAndGet()
            emptyList()
        }
        assertEquals(2, calls.get())
    }

    /**
     * 1500 IDs → 3 chunks (500 + 500 + 500).
     */
    @Test
    fun fifteenHundredIdsProducesThreeChunks() = runTest {
        val ids = (1..1500).toList()
        val calls = AtomicInteger(0)
        ByIdsChunker.fetchInChunks<Int>(ids) { _ ->
            calls.incrementAndGet()
            emptyList()
        }
        assertEquals(3, calls.get())
    }

    /**
     * Los resultados de múltiples chunks se concatenan correctamente.
     */
    @Test
    fun resultsFromMultipleChunksAreFlattenedInOrder() = runTest {
        // 600 IDs → chunk1=[1..500], chunk2=[501..600]
        val ids = (1..600).toList()
        val result = ByIdsChunker.fetchInChunks(ids) { csv ->
            csv.split(",").map { it.trim().toInt() }
        }
        assertEquals(ids, result.sorted())
    }

    // ─── Cap de concurrencia ─────────────────────────────────────────────────

    /**
     * Con 1500 IDs (3 chunks), el semáforo limita a máximo CONCURRENCY=3
     * llamadas inflight simultáneamente. Se verifica que nunca hay más de 3
     * coroutines activas al mismo tiempo usando un contador de inflight y un
     * semáforo de control.
     */
    @Test
    fun concurrencyNeverExceedsLimit() = runTest {
        val limit = ByIdsChunker.BY_IDS_LIMIT
        // Generamos exactamente CONCURRENCY+1 chunks para forzar contención.
        val chunkCount = 4 // CONCURRENCY (3) + 1
        val ids = (1..(limit * chunkCount)).toList()

        val inflight = AtomicInteger(0)
        val maxObservedInflight = AtomicInteger(0)

        ByIdsChunker.fetchInChunks<Int>(ids) { _ ->
            val current = inflight.incrementAndGet()
            // Registrar el pico de concurrencia.
            maxObservedInflight.getAndUpdate { prev -> maxOf(prev, current) }
            // Pequeña espera para dar oportunidad a otras coroutines de avanzar.
            kotlinx.coroutines.delay(10)
            inflight.decrementAndGet()
            emptyList()
        }

        assertTrue(
            "La concurrencia máxima observada (${maxObservedInflight.get()}) " +
                "no debe superar CONCURRENCY=${ByIdsChunker.BY_IDS_LIMIT / 500 * 3}",
            maxObservedInflight.get() <= 3
        )
    }

    // ─── Feature-flag 404 ────────────────────────────────────────────────────

    /**
     * Cuando fetch lanza HttpException(404), byIdsAvailable se latchea a false.
     */
    @Test
    fun httpException404SetsAvailableFalse() = runTest {
        assertTrue(ByIdsChunker.byIdsAvailable.get())

        var threw = false
        try {
            ByIdsChunker.fetchInChunks<Int>(listOf(1)) { _ ->
                // Simulamos un HttpException con código 404.
                val retrofitResponse = Response.error<Unit>(
                    404,
                    "".toResponseBody(null)
                )
                throw HttpException(retrofitResponse)
            }
        } catch (_: HttpException) {
            threw = true
        }

        assertTrue("La excepción debe propagarse al caller", threw)
        assertFalse(
            "byIdsAvailable debe latchar a false tras 404",
            ByIdsChunker.byIdsAvailable.get()
        )
    }

    /**
     * Cuando fetch lanza HttpException(500) (error genérico), byIdsAvailable
     * permanece true — solo 404 la apaga.
     */
    @Test
    fun httpException500DoesNotChangeAvailableFlag() = runTest {
        assertTrue(ByIdsChunker.byIdsAvailable.get())

        try {
            ByIdsChunker.fetchInChunks<Int>(listOf(1)) { _ ->
                val retrofitResponse = Response.error<Unit>(
                    500,
                    "".toResponseBody(null)
                )
                throw HttpException(retrofitResponse)
            }
        } catch (_: HttpException) { /* expected */ }

        assertTrue(
            "Un 500 no debe tocar byIdsAvailable",
            ByIdsChunker.byIdsAvailable.get()
        )
    }
}
