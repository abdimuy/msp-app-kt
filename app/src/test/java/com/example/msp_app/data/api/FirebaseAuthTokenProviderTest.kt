package com.example.msp_app.data.api

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura de la caché de token de [FirebaseAuthTokenProvider] (reescritura del
 * `TokenCache` legacy). Fakes-only: se inyecta un `fetch` que graba sus llamadas
 * y un reloj controlable — sin Firebase ni MockK.
 *
 * Contrato ejercido (auditado vs backend Go):
 *  - `token(false)` cachea ~50 min; dentro de la ventana NO vuelve a llamar a
 *    Firebase.
 *  - Pasada la TTL, refresca.
 *  - `token(true)` siempre fuerza `getIdToken(true)` y refresca la caché.
 *  - `null` (sin usuario) NO se cachea ni pisa un token válido previo.
 */
class FirebaseAuthTokenProviderTest {

    /** Fetch grabador: registra cada `forceRefresh` y devuelve un token programable. */
    private class RecordingFetch(var next: String? = "token-1") {
        val calls = mutableListOf<Boolean>()

        val fetch: suspend (Boolean) -> String? = { forceRefresh ->
            calls.add(forceRefresh)
            next
        }
    }

    /** Reloj mutable en millis. */
    private class FakeClock(var now: Long = 0L) {
        val read: () -> Long = { now }
    }

    private val ttlMillis = 50L * 60L * 1_000L

    @Test
    fun `token(false) primera vez llama a fetch(false) y devuelve el token`() = runTest {
        val fetch = RecordingFetch(next = "token-1")
        val provider = FirebaseAuthTokenProvider(fetch = fetch.fetch, clock = FakeClock().read)

        assertEquals("token-1", provider.token(forceRefresh = false))
        assertEquals(listOf(false), fetch.calls)
    }

    @Test
    fun `token(false) dentro de la TTL devuelve el cacheado sin volver a fetch`() = runTest {
        val fetch = RecordingFetch(next = "token-1")
        val clock = FakeClock(now = 0L)
        val provider = FirebaseAuthTokenProvider(fetch = fetch.fetch, clock = clock.read)

        provider.token(forceRefresh = false)
        clock.now = ttlMillis - 1 // justo dentro de la ventana
        val second = provider.token(forceRefresh = false)

        assertEquals("token-1", second)
        assertEquals("una sola llamada a Firebase dentro de la TTL", 1, fetch.calls.size)
    }

    @Test
    fun `token(false) en el borde exacto de la TTL vuelve a fetch`() = runTest {
        val fetch = RecordingFetch(next = "token-1")
        val clock = FakeClock(now = 0L)
        val provider = FirebaseAuthTokenProvider(fetch = fetch.fetch, clock = clock.read)

        provider.token(forceRefresh = false)
        fetch.next = "token-2"
        clock.now = ttlMillis // now - fetchedAt == TTL → NO vigente (usa `<`)
        val second = provider.token(forceRefresh = false)

        assertEquals("token-2", second)
        assertEquals(listOf(false, false), fetch.calls)
    }

    @Test
    fun `token(false) pasada la TTL refresca con fetch(false)`() = runTest {
        val fetch = RecordingFetch(next = "token-1")
        val clock = FakeClock(now = 0L)
        val provider = FirebaseAuthTokenProvider(fetch = fetch.fetch, clock = clock.read)

        provider.token(forceRefresh = false)
        fetch.next = "token-2"
        clock.now = ttlMillis + 1
        val second = provider.token(forceRefresh = false)

        assertEquals("token-2", second)
        assertEquals(listOf(false, false), fetch.calls)
    }

    @Test
    fun `token(true) siempre llama a fetch(true) aunque haya cache vigente`() = runTest {
        val fetch = RecordingFetch(next = "token-1")
        val clock = FakeClock(now = 0L)
        val provider = FirebaseAuthTokenProvider(fetch = fetch.fetch, clock = clock.read)

        provider.token(forceRefresh = false) // caché vigente
        fetch.next = "fresh"
        val refreshed = provider.token(forceRefresh = true)

        assertEquals("fresh", refreshed)
        assertEquals(listOf(false, true), fetch.calls)
    }

    @Test
    fun `token(true) actualiza la cache y luego token(false) da el fresco`() = runTest {
        val fetch = RecordingFetch(next = "token-1")
        val clock = FakeClock(now = 0L)
        val provider = FirebaseAuthTokenProvider(fetch = fetch.fetch, clock = clock.read)

        provider.token(forceRefresh = false)
        fetch.next = "fresh"
        provider.token(forceRefresh = true)
        val afterRefresh = provider.token(forceRefresh = false)

        assertEquals("fresh", afterRefresh)
        // false (inicial) + true (refresh); la 3ra token(false) sale de caché.
        assertEquals(listOf(false, true), fetch.calls)
    }

    @Test
    fun `token(false) sin usuario devuelve null y no cachea`() = runTest {
        val fetch = RecordingFetch(next = null)
        val provider = FirebaseAuthTokenProvider(fetch = fetch.fetch, clock = FakeClock().read)

        assertNull(provider.token(forceRefresh = false))
        assertNull(provider.token(forceRefresh = false))
        assertEquals("null no se cachea: cada llamada reintenta", 2, fetch.calls.size)
    }

    @Test
    fun `un fetch null no pisa un token valido previo (misma ventana TTL)`() = runTest {
        val fetch = RecordingFetch(next = "token-1")
        val clock = FakeClock(now = 0L)
        val provider = FirebaseAuthTokenProvider(fetch = fetch.fetch, clock = clock.read)

        provider.token(forceRefresh = false) // cachea token-1
        fetch.next = null // el usuario "desaparece" a mitad de sesión
        clock.now = ttlMillis + 1 // fuerza refetch → devuelve null
        assertNull(provider.token(forceRefresh = false))

        // El token-1 previo NO fue pisado: dentro de una ventana nueva vuelve.
        fetch.next = "token-1"
        clock.now = ttlMillis + 2
        assertEquals("token-1", provider.token(forceRefresh = false))
    }

    @Test
    fun `el forceRefresh se propaga tal cual a fetch`() = runTest {
        val fetch = RecordingFetch(next = "t")
        val provider = FirebaseAuthTokenProvider(fetch = fetch.fetch, clock = FakeClock().read)

        provider.token(forceRefresh = true)

        assertTrue("fetch debe recibir forceRefresh=true", fetch.calls.single())
    }
}
