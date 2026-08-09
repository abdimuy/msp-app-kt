package com.example.msp_app.core.network

import java.net.HttpURLConnection.HTTP_OK
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifica que [AppVersionInterceptor] adjunta `X-App-Version` con el valor
 * configurado a cada request, contra un [MockWebServer] real.
 */
class AppVersionInterceptorTest {

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

    @Test
    fun `adjunta X-App-Version con el valor configurado`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK))
        val client = OkHttpClient.Builder()
            .addInterceptor(AppVersionInterceptor(appVersion = "2.12.2"))
            .build()

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        val recorded = server.takeRequest()
        assertEquals("2.12.2", recorded.getHeader(AppVersionInterceptor.HEADER_APP_VERSION))
    }

    @Test
    fun `el nombre del header es X-App-Version`() {
        assertEquals("X-App-Version", AppVersionInterceptor.HEADER_APP_VERSION)
    }
}
