package com.example.msp_app.data.repository

import com.example.msp_app.core.common.busqueda.TiempoAgotadoException
import com.example.msp_app.core.common.busqueda.conTopeTotal
import com.example.msp_app.data.api.services.clientes.ClientesApi
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * E-APP-158, con red REAL (MockWebServer + OkHttp + Retrofit): un servidor que gotea bytes
 * lentamente.
 *
 * El `readTimeout` de OkHttp mide INACTIVIDAD entre bytes: mientras llegue un byte antes de que
 * venza, la llamada vive. Por eso el cliente legado (300 s) dejaba el botón girando sin límite.
 * La primera prueba lo demuestra (control: el read timeout solo NO corta); la segunda muestra que
 * el tope TOTAL sí corta, y que corta de verdad la petición (OkHttp reporta `canceled`).
 */
class DescargaConTopeHttpTest {

    private lateinit var servidor: MockWebServer
    private val cancelaciones = AtomicInteger()

    private val cuerpo = """{"body":[{"CLIENTE_ID":1,"NOMBRE":"ANA RUIZ","ESTATUS":"A","CAUSA_SUSP":null}]}"""

    private fun api(readTimeoutMs: Long): ClientesApi {
        val http = OkHttpClient.Builder()
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .eventListener(object : EventListener() {
                override fun canceled(call: Call) {
                    cancelaciones.incrementAndGet()
                }
            })
            .build()
        return Retrofit.Builder()
            .baseUrl(servidor.url("/"))
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ClientesApi::class.java)
    }

    @Before fun arrancar() {
        servidor = MockWebServer()
        // ~104 bytes a 1 byte cada 30 ms = ~3 s en total, sin pasar nunca 30 ms sin recibir un byte.
        servidor.enqueue(MockResponse().setBody(cuerpo).throttleBody(1, 30, TimeUnit.MILLISECONDS))
        servidor.start()
    }

    @After fun apagar() {
        servidor.shutdown()
    }

    @Test fun `control el read timeout solo no corta una descarga que gotea`() {
        val inicio = System.nanoTime()
        val respuesta = runBlocking { api(readTimeoutMs = 1_000).getClientes() }
        val ms = (System.nanoTime() - inicio) / 1_000_000
        assertEquals(1, respuesta.body.size)
        assertTrue(
            "la descarga debía tardar más de 2 s con read timeout de 1 s: $ms ms",
            ms > 2_000
        )
        assertEquals(0, cancelaciones.get())
    }

    @Test fun `el tope total corta la misma descarga y cancela la peticion de verdad`() {
        val inicio = System.nanoTime()
        val error = runBlocking {
            runCatching {
                conTopeTotal(
                    1_000
                ) { api(readTimeoutMs = 1_000).getClientes() }
            }.exceptionOrNull()
        }
        val ms = (System.nanoTime() - inicio) / 1_000_000
        assertTrue("debía lanzar TiempoAgotadoException: $error", error is TiempoAgotadoException)
        assertTrue("debía cortar cerca de 1 s, no a los 3 s: $ms ms", ms in 900..2_000)
        assertTrue("OkHttp debe reportar la llamada cancelada", cancelaciones.get() >= 1)
    }
}
