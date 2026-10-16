package com.example.msp_app.core.network

import com.example.msp_app.core.network.diagnostico.EnvioDiagnosticoSink
import com.example.msp_app.core.network.diagnostico.EnvioObservado
import com.example.msp_app.core.network.diagnostico.PagosDePruebaApi
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Provider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * E-APP-064: el interceptor de diagnóstico entra SOLO en el perfil v2 (el del
 * `POST /v2/cobranza/pagos`) y solo cuando se le da un destino. El constructor
 * de Hilt no lo lleva.
 */
class RetrofitClientFactoryDiagnosticoTest {

    private lateinit var server: MockWebServer
    private val observados = CopyOnWriteArrayList<EnvioObservado>()
    private val sink = EnvioDiagnosticoSink { observados += it }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config() = NetworkConfig(
        legacyBaseUrl = server.url("/").toString(),
        v2BaseUrl = server.url("/").toString(),
        imagesBaseUrl = server.url("/").toString(),
        appVersion = "2.20.0"
    )

    private fun postPago(retrofit: Retrofit) {
        server.enqueue(MockResponse().setResponseCode(503))
        retrofit.create(PagosDePruebaApi::class.java)
            .crearPago("pago-1", "{}".toRequestBody("application/json".toMediaType()))
            .execute()
    }

    @Test
    fun `E-APP-064 v2 con destino observa el POST de pagos`() {
        val factory =
            RetrofitClientFactory(Provider { config() }, FakeAuthTokenProvider("tok"), sink)
        postPago(factory.v2())
        assertEquals("http_503", observados.single().fallo!!.codigo)
        // Sigue llevando bearer: el interceptor nuevo no desplazó a nadie.
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `legacy, images y create no observan aunque haya destino`() {
        val factory =
            RetrofitClientFactory(Provider { config() }, FakeAuthTokenProvider("tok"), sink)
        postPago(factory.legacy())
        postPago(factory.images())
        postPago(factory.create(server.url("/").toString(), auth = true, appVersionHeader = true))
        assertTrue(observados.isEmpty())
    }

    @Test
    fun `el constructor de Hilt no lleva diagnostico`() {
        val factory = RetrofitClientFactory(Provider { config() }, FakeAuthTokenProvider("tok"))
        postPago(factory.v2())
        assertTrue(observados.isEmpty())
    }
}
