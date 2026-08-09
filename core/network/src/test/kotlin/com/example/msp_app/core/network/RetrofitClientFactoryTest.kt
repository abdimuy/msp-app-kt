package com.example.msp_app.core.network

import java.net.HttpURLConnection.HTTP_OK
import javax.inject.Provider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.http.GET

/** DTO trivial para probar el pipeline Gson del factory. */
data class Pong(val status: String)

/** Servicio Retrofit de prueba. */
interface PingApi {
    @GET("ping")
    fun ping(): retrofit2.Call<Pong>
}

/**
 * Cobertura suprema de [RetrofitClientFactory]: composición de interceptores por
 * perfil, deserialización Gson, y —lo crítico— la **prueba del kill-switch**:
 * un cambio de baseURL en la [NetworkConfig] inyectada alcanza a la siguiente
 * request (la baseURL NO queda congelada).
 */
class RetrofitClientFactoryTest {

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

    private fun configFor(baseUrl: String): NetworkConfig = NetworkConfig(
        legacyBaseUrl = baseUrl,
        v2BaseUrl = baseUrl,
        imagesBaseUrl = baseUrl,
        appVersion = "2.12.2"
    )

    private fun factoryFor(
        configProvider: Provider<NetworkConfig>,
        tokenProvider: AuthTokenProvider = FakeAuthTokenProvider(initialToken = "tok")
    ): RetrofitClientFactory = RetrofitClientFactory(configProvider, tokenProvider)

    private fun pingWith(retrofit: Retrofit) {
        retrofit.create(PingApi::class.java).ping().execute().body()
    }

    @Test
    fun `create con auth true incluye bearer y version`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("""{"status":"ok"}"""))
        val factory = factoryFor(Provider { configFor(server.url("/").toString()) })

        pingWith(factory.create(server.url("/").toString(), auth = true))

        val recorded = server.takeRequest()
        assertEquals("Bearer tok", recorded.getHeader("Authorization"))
        assertEquals("2.12.2", recorded.getHeader(AppVersionInterceptor.HEADER_APP_VERSION))
    }

    @Test
    fun `create con auth false solo incluye version sin bearer`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("""{"status":"ok"}"""))
        val factory = factoryFor(Provider { configFor(server.url("/").toString()) })

        pingWith(factory.create(server.url("/").toString(), auth = false))

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertEquals("2.12.2", recorded.getHeader(AppVersionInterceptor.HEADER_APP_VERSION))
    }

    @Test
    fun `el converter Gson deserializa el cuerpo de la respuesta`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("""{"status":"listo"}"""))
        val factory = factoryFor(Provider { configFor(server.url("/").toString()) })

        val body = factory.create(server.url("/").toString(), auth = false)
            .create(PingApi::class.java).ping().execute().body()

        assertNotNull(body)
        assertEquals("listo", body?.status)
    }

    @Test
    fun `v2 usa bearer y legacy no`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("""{"status":"ok"}"""))
        val factory = factoryFor(Provider { configFor(server.url("/").toString()) })

        pingWith(factory.v2())
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))

        pingWith(factory.legacy())
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    /**
     * PRUEBA DEL KILL-SWITCH (garantía de seguridad de la money-app): cambiar la
     * baseURL en la [NetworkConfig] inyectada DEBE reflejarse en la siguiente
     * request. Si el factory congelara la baseURL, la segunda request seguiría
     * llegando al primer servidor y el kill-switch remoto quedaría inutilizado.
     */
    @Test
    fun `killswitch un cambio de baseURL alcanza la siguiente request`() {
        val server2 = MockWebServer()
        server2.start()
        try {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("""{"status":"ok"}"""))
            server2.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("""{"status":"ok"}"""))

            // Provider mutable: emula el rebuild de baseURL por Firestore de `:app`.
            var config = configFor(server.url("/").toString())
            val factory = factoryFor(Provider { config })

            // Primera request → servidor 1.
            pingWith(factory.v2())
            assertEquals(1, server.requestCount)
            assertEquals(0, server2.requestCount)

            // Flip de baseURL (kill-switch) → la siguiente request va al servidor 2.
            config = configFor(server2.url("/").toString())
            pingWith(factory.v2())

            assertEquals(1, server.requestCount)
            assertEquals(1, server2.requestCount)
        } finally {
            server2.shutdown()
        }
    }
}
