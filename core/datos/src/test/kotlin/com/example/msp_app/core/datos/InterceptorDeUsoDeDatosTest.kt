package com.example.msp_app.core.datos

import com.example.msp_app.core.testing.time.FakeClock
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import okio.BufferedSink
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * E-APP-171: el contador de bytes por servicio. La regla dura: NUNCA altera,
 * retrasa, falla ni reintenta una petición (pasa por el POST de pagos).
 */
class InterceptorDeUsoDeDatosTest {

    private lateinit var server: MockWebServer
    private val clock = FakeClock.at("2026-11-10T18:00:00Z")
    private val registro = RegistroDeUso(clock)

    /** Lo que el espía ve en el cable, DESPUÉS de nuestro interceptor (más cerca de la red). */
    private val respuestasVistas = java.util.concurrent.CopyOnWriteArrayList<Response>()
    private val espia = Interceptor { chain ->
        val r = chain.proceed(chain.request())
        respuestasVistas += r
        r
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun cliente(
        contador: ContadorDeBytes = registro,
        fija: String? = null,
        reintentos: Boolean = true
    ): OkHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(InterceptorDeUsoDeDatos(contador, fija))
        .addNetworkInterceptor(espia)
        .retryOnConnectionFailure(reintentos)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun clavePrimera(): Map.Entry<String, UsoDeEndpoint> =
        registro.instantanea().single().endpoints.entries.single()

    private fun leerHastaElFinal(src: okio.BufferedSource, leidos: LongArray) {
        val buf = ByteArray(8192)
        while (true) {
            val n = src.read(buf)
            if (n < 0) return
            leidos[0] += n
        }
    }

    private fun cuerpo(n: Int) = ByteArray(n) { (it % 251).toByte() }

    private fun gzip(b: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        GZIPOutputStream(o).use { it.write(b) }
        return o.toByteArray()
    }

    @Test fun `cuenta exacto el cuerpo de respuesta y la estimacion de cabeceras`() {
        server.enqueue(MockResponse().setBody(Buffer().write(cuerpo(1000))))
        val c = cliente()
        c.newCall(Request.Builder().url(server.url("/v2/cobranza/pagos/55")).build()).execute()
            .use { it.body!!.bytes() }
        val e = clavePrimera()
        assertEquals("GET /v2/cobranza/pagos/{id}", e.key)
        val r = respuestasVistas.single()
        assertEquals(1000L + Estimacion.cabecerasDeRespuesta(r), e.value.rx)
        assertEquals(Estimacion.cabecerasDePeticion(r.request), e.value.tx)
        assertEquals(1L, e.value.peticiones)
    }

    @Test fun `con gzip cuenta los bytes comprimidos del cable`() {
        val original = ByteArray(20_000) { 'a'.code.toByte() }
        val comprimido = gzip(original)
        assertTrue(comprimido.size < 1000)
        server.enqueue(
            MockResponse().setHeader("Content-Encoding", "gzip").setBody(Buffer().write(comprimido))
        )
        val c = cliente()
        val leido = c.newCall(Request.Builder().url(server.url("/clientes/")).build()).execute()
            .use { it.body!!.bytes() }
        assertEquals(20_000, leido.size) // la app sigue viendo el cuerpo descomprimido
        val e = clavePrimera()
        assertEquals(
            comprimido.size + Estimacion.cabecerasDeRespuesta(respuestasVistas.single()),
            e.value.rx
        )
    }

    private fun multipart(imagen: ByteArray?): MultipartBody = MultipartBody.Builder().setType(
        MultipartBody.FORM
    )
        .addFormDataPart("datos", """{"monto":100}""")
        .apply {
            if (imagen != null) {
                addFormDataPart(
                    "comprobante",
                    "a.jpg",
                    imagen.toRequestBody("image/jpeg".toMediaType())
                )
            }
        }
        .build()

    private fun enviar(ruta: String, cuerpo: RequestBody): Long {
        server.enqueue(MockResponse().setBody("ok"))
        cliente().newCall(Request.Builder().url(server.url(ruta)).post(cuerpo).build())
            .execute().use { it.body!!.string() }
        return server.takeRequest().bodySize
    }

    // H4: un pago multipart SIN foto es "Pagos y ventas"; no se marca como foto.
    @Test fun `pago multipart sin foto cuenta en su ruta y no en fotos`() {
        val enviado = enviar("/v2/cobranza/pagos", multipart(null))
        val dia = registro.instantanea().single().endpoints
        assertEquals(setOf("POST /v2/cobranza/pagos"), dia.keys)
        assertEquals(
            enviado + Estimacion.cabecerasDePeticion(respuestasVistas.single().request),
            dia.getValue("POST /v2/cobranza/pagos").tx
        )
    }

    // H4: con foto, los bytes de la parte image/* van a fotos y el resto a su ruta; el total no cambia.
    @Test fun `con foto los bytes de la imagen van a fotos y el resto a su ruta`() {
        val enviado = enviar("/v2/visitas", multipart(cuerpo(50_000)))
        val dia = registro.instantanea().single().endpoints
        val cab = Estimacion.cabecerasDePeticion(respuestasVistas.single().request)
        val foto = dia.getValue(ClasificadorDeServicio.CLAVE_FOTOS)
        assertEquals(50_000L, foto.tx)
        assertEquals(1L, foto.peticiones)
        assertEquals(enviado - 50_000 + cab, dia.getValue("POST /v2/visitas").tx)
        assertEquals(enviado + cab, dia.values.sumOf { it.tx })
    }

    @Test fun `dos fotos son dos y un cuerpo que no es multipart no crea fotos`() {
        val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("a", "a.jpg", cuerpo(10).toRequestBody("image/jpeg".toMediaType()))
            .addFormDataPart("b", "b.png", cuerpo(20).toRequestBody("image/png".toMediaType()))
            .addFormDataPart(
                "c",
                "c.pdf",
                cuerpo(30).toRequestBody("application/pdf".toMediaType())
            )
            .build()
        enviar("/v2/ventas", mp)
        val foto = registro.instantanea().single().endpoints.getValue(
            ClasificadorDeServicio.CLAVE_FOTOS
        )
        assertEquals(30L, foto.tx)
        assertEquals(2L, foto.peticiones)
    }

    // H1: un cuerpo que cierra el sink debe dar la misma respuesta con y sin contador.
    @Test fun `un cuerpo que cierra el sink se envia igual y no truena`() {
        val datos = cuerpo(4000)
        val cierra = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun writeTo(sink: BufferedSink) {
                sink.outputStream().use { it.write(datos) }
            }
        }
        server.enqueue(MockResponse().setBody("ok"))
        val resp = cliente().newCall(
            Request.Builder().url(server.url("/v2/ventas")).post(cierra).build()
        ).execute()
        assertEquals(200, resp.use { it.code })
        assertArrayEquals(datos, server.takeRequest().body.readByteArray())
    }

    // M2 (revisión): la excepción de la cadena se relanza TAL CUAL, no envuelta en otra.
    @Test fun `la excepcion de la cadena sale la misma instancia`() {
        val boom = IOException("boom propio")
        val lanza = Interceptor { throw boom }
        val c = OkHttpClient.Builder()
            .addNetworkInterceptor(InterceptorDeUsoDeDatos(registro))
            .addNetworkInterceptor(lanza)
            .retryOnConnectionFailure(false)
            .build()
        server.enqueue(MockResponse().setBody("x"))
        val salio = try {
            c.newCall(Request.Builder().url(server.url("/ping")).build()).execute()
            null
        } catch (e: IOException) {
            e
        }
        assertSame(boom, salio)
    }

    // M3 (revisión): `source()` devuelve SIEMPRE la misma fuente; si no, `peekBody` de otros
    // interceptores (el de diagnóstico del POST de pagos) se come los bytes del cuerpo.
    @Test fun `source es la misma y peekBody no consume el cuerpo`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error-body-1234567890"))
        val c = OkHttpClient.Builder().addNetworkInterceptor(
            InterceptorDeUsoDeDatos(registro)
        ).build()
        c.newCall(Request.Builder().url(server.url("/ping")).build()).execute().use { r ->
            val body = r.body!!
            assertSame(body.source(), body.source())
            assertEquals("error-body-1234567890", r.peekBody(8L * 1024).string())
            assertEquals("error-body-1234567890", body.string())
        }
    }

    @Test fun `respuesta cortada a la mitad cuenta lo que alcanzo a llegar`() {
        val total = 400_000
        server.enqueue(
            MockResponse().setBody(Buffer().write(cuerpo(total)))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        val leidos = LongArray(1)
        try {
            cliente().newCall(
                Request.Builder().url(server.url("/imagenes")).build()
            ).execute().use { leerHastaElFinal(it.body!!.source(), leidos) }
            fail("debía cortarse")
        } catch (_: IOException) {
            // esperado: el servidor corta a media respuesta
        }
        val rx = clavePrimera().value.rx
        val cabeceras = Estimacion.cabecerasDeRespuesta(respuestasVistas.single())
        assertTrue("llegó algo", rx > cabeceras)
        assertTrue("menos que el total", rx < total + cabeceras)
        assertTrue("al menos lo que la app leyó", rx - cabeceras >= leidos[0])
    }

    @Test fun `sse que gotea cuenta incrementalmente mientras la conexion sigue abierta`() {
        val evento = "data: " + "x".repeat(90) + "\n\n" // 98 bytes
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(evento.repeat(30))
                .throttleBody(196, 200, TimeUnit.MILLISECONDS)
        )
        val resp = cliente().newCall(
            Request.Builder().url(server.url("/v2/cobranza/sync/pagos/zona/3/stream")).build()
        )
            .execute()
        resp.use {
            val src = it.body!!.source()
            src.readUtf8Line()
            val temprano = clavePrimera().value.rx
            repeat(6) { _ -> src.readUtf8Line() }
            val despues = clavePrimera().value.rx
            assertTrue("crece mientras corre: $temprano -> $despues", despues > temprano)
            assertTrue("aún no termina", despues < evento.length * 30 + 400)
            assertEquals("GET /v2/cobranza/sync/pagos/zona/{id}/stream", clavePrimera().key)
        }
    }

    @Test fun `peticion cancelada propaga el error y cuenta la peticion`() {
        server.enqueue(
            MockResponse().setBody(Buffer().write(cuerpo(200_000)))
                .throttleBody(1000, 100, TimeUnit.MILLISECONDS)
        )
        val call = cliente().newCall(Request.Builder().url(server.url("/imagenes")).build())
        val resp = call.execute()
        val hilo = Thread {
            Thread.sleep(300)
            call.cancel()
        }
        hilo.start()
        try {
            resp.use { it.body!!.bytes() }
            fail("debía cancelarse")
        } catch (_: IOException) {
            assertTrue(call.isCanceled())
        }
        assertEquals(1L, clavePrimera().value.peticiones)
    }

    @Test fun `error de red despues de enviar cuenta lo enviado y relanza el mismo error`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val sinContador = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        val esperado = try {
            sinContador.newCall(Request.Builder().url(server.url("/ping")).build()).execute()
            null
        } catch (e: IOException) {
            e.javaClass
        }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val obtenido = try {
            cliente(
                reintentos = false
            ).newCall(Request.Builder().url(server.url("/ping")).build()).execute()
            null
        } catch (e: IOException) {
            e.javaClass
        }
        assertEquals(esperado, obtenido)
        val e = clavePrimera().value
        assertEquals(1L, e.peticiones)
        assertTrue("contó lo que mandó", e.tx > 0)
        assertEquals(0L, e.rx)
    }

    @Test fun `no altera la peticion ni la respuesta`() {
        val cuerpoPeticion = cuerpo(30_000)
        val peticion = {
            Request.Builder().url(server.url("/v2/cobranza/pagos"))
                .header("Idempotency-Key", "abc")
                .post(cuerpoPeticion.toRequestBody("application/octet-stream".toMediaType()))
                .build()
        }
        fun respuesta() = MockResponse().setResponseCode(201).setHeader("X-Uno", "1")
            .setHeader("Content-Type", "application/json").setBody("""{"ok":true}""")
        server.enqueue(respuesta())
        server.enqueue(respuesta())

        val sin = OkHttpClient.Builder().build().newCall(peticion()).execute()
        val sinCuerpo = sin.body!!.bytes()
        val rSin = server.takeRequest()
        val con = cliente().newCall(peticion()).execute()
        val conCuerpo = con.body!!.bytes()
        val rCon = server.takeRequest()

        assertEquals(sin.code, con.code)
        assertEquals(sin.message, con.message)
        assertEquals(sin.header("X-Uno"), con.header("X-Uno"))
        assertEquals(sin.body!!.contentType(), con.body!!.contentType())
        assertEquals(sin.body!!.contentLength(), con.body!!.contentLength())
        assertArrayEquals(sinCuerpo, conCuerpo)
        assertEquals(rSin.method, rCon.method)
        assertEquals(rSin.path, rCon.path)
        assertEquals(rSin.headers.toMultimap(), rCon.headers.toMultimap())
        assertArrayEquals(rSin.body.readByteArray(), rCon.body.readByteArray())
    }

    @Test fun `un cuerpo de longitud desconocida se envia identico`() {
        server.enqueue(MockResponse().setBody("ok"))
        val datos = cuerpo(70_000)
        val streaming = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = -1L
            override fun writeTo(sink: BufferedSink) {
                sink.write(datos)
            }
        }
        cliente().newCall(Request.Builder().url(server.url("/v2/ventas")).post(streaming).build())
            .execute().use { it.body!!.string() }
        val r = server.takeRequest()
        assertEquals("chunked", r.getHeader("Transfer-Encoding"))
        assertArrayEquals(datos, r.body.readByteArray())
        val tx = clavePrimera().value.tx
        assertTrue(tx >= 70_000)
    }

    @Test fun `una excepcion del contador no rompe la peticion ni la lectura`() {
        val roto = object : ContadorDeBytes {
            override fun peticion(clave: String) = error("contador roto")
            override fun bytes(clave: String, rx: Long, tx: Long) = error("contador roto")
        }
        val datos = cuerpo(5000)
        server.enqueue(MockResponse().setBody(Buffer().write(datos)))
        val resp = cliente(contador = roto).newCall(
            Request.Builder().url(server.url("/v2/ventas"))
                .post(datos.toRequestBody("application/octet-stream".toMediaType())).build()
        ).execute()
        assertEquals(200, resp.code)
        assertArrayEquals(datos, resp.use { it.body!!.bytes() })
        assertArrayEquals(datos, server.takeRequest().body.readByteArray())
    }

    @Test fun `claveFija agrupa todo bajo una sola clave`() {
        server.enqueue(MockResponse().setBody("x"))
        cliente(fija = ClasificadorDeServicio.CLAVE_IMAGENES)
            .newCall(
                Request.Builder().url(server.url("/algo/12345/foto.jpg?token=secreto")).build()
            )
            .execute().use { it.body!!.string() }
        assertEquals(ClasificadorDeServicio.CLAVE_IMAGENES, clavePrimera().key)
    }

    @Test fun `cientos de peticiones en paralelo suman exacto`() {
        val n = 300
        val tam = 777
        repeat(n) { server.enqueue(MockResponse().setBody(Buffer().write(cuerpo(tam)))) }
        val c = cliente()
        c.dispatcher.maxRequests = n
        c.dispatcher.maxRequestsPerHost = n
        val pool = Executors.newFixedThreadPool(32)
        val listos = CountDownLatch(n)
        repeat(n) { i ->
            pool.execute {
                try {
                    c.newCall(Request.Builder().url(server.url("/v2/visitas/$i")).build()).execute()
                        .use { it.body!!.bytes() }
                } finally {
                    listos.countDown()
                }
            }
        }
        assertTrue(listos.await(60, TimeUnit.SECONDS))
        pool.shutdown()
        val e = clavePrimera()
        assertEquals("GET /v2/visitas/{id}", e.key)
        assertEquals(n.toLong(), e.value.peticiones)
        val esperadoRx = respuestasVistas.sumOf { tam + Estimacion.cabecerasDeRespuesta(it) }
        val esperadoTx = respuestasVistas.sumOf { Estimacion.cabecerasDePeticion(it.request) }
        assertEquals(n, respuestasVistas.size)
        assertEquals(esperadoRx, e.value.rx)
        assertEquals(esperadoTx, e.value.tx)
    }
}
