package com.example.msp_app.core.network

import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Cobertura suprema del contrato de auth de [BearerAuthInterceptor] contra un
 * [MockWebServer] real (sin MockK). Verifica los cuatro caminos: token presente,
 * sin token (passthrough), 401-con-token (refresh + un reintento) y 401-sin-token
 * (sin bucle de reintento).
 */
class BearerAuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWith(provider: FakeAuthTokenProvider): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BearerAuthInterceptor(provider))
        .build()

    private fun get(client: OkHttpClient) {
        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
    }

    @Test
    fun `con token adjunta Authorization Bearer`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK))
        val provider = FakeAuthTokenProvider(initialToken = "tok-123")

        get(clientWith(provider))

        val recorded = server.takeRequest()
        assertEquals("Bearer tok-123", recorded.getHeader("Authorization"))
        assertEquals(1, provider.normalCalls)
        assertEquals(0, provider.refreshCalls)
    }

    @Test
    fun `sin token pasa sin header Authorization`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK))
        val provider = FakeAuthTokenProvider(initialToken = null)

        get(clientWith(provider))

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `en 401 con token refresca y reintenta una vez con el token fresco`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED))
        server.enqueue(MockResponse().setResponseCode(HTTP_OK))
        val provider = FakeAuthTokenProvider(initialToken = "stale", refreshedToken = "fresh")

        get(clientWith(provider))

        assertEquals(2, server.requestCount)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer stale", first.getHeader("Authorization"))
        assertEquals("Bearer fresh", second.getHeader("Authorization"))
        assertEquals(1, provider.refreshCalls)
    }

    @Test
    fun `en doble 401 reintenta una sola vez y devuelve el segundo 401 sin bucle`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED))
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED))
        val provider = FakeAuthTokenProvider(initialToken = "stale", refreshedToken = "fresh")

        val code = clientWith(provider)
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()
            .use { it.code }

        assertEquals(HTTP_UNAUTHORIZED, code)
        assertEquals(2, server.requestCount)
        assertEquals(1, provider.refreshCalls)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer stale", first.getHeader("Authorization"))
        assertEquals("Bearer fresh", second.getHeader("Authorization"))
    }

    @Test
    fun `en 401 sin token previo no reintenta en bucle`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED))
        val provider = FakeAuthTokenProvider(initialToken = null)

        get(clientWith(provider))

        assertEquals(1, server.requestCount)
        assertEquals(0, provider.refreshCalls)
    }

    @Test
    fun `en 401 con token pero refresh nulo reintenta una vez sin header`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED))
        server.enqueue(MockResponse().setResponseCode(HTTP_OK))
        val provider = FakeAuthTokenProvider(initialToken = "stale", refreshedToken = null)

        get(clientWith(provider))

        assertEquals(2, server.requestCount)
        server.takeRequest()
        val second = server.takeRequest()
        assertNull(second.getHeader("Authorization"))
        assertEquals(1, provider.refreshCalls)
    }
}
