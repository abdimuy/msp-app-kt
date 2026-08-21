package com.example.msp_app.core.network

import java.io.IOException
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.util.concurrent.CountDownLatch
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * El proveedor de identidad no siempre contesta, y eso NO puede tumbar la app.
 *
 * Incidente que estas pruebas fijan (2026-08-21, versión 2.17.2 en producción):
 * un teléfono sin señal más de ~50 minutos —lo que dura el caché del ID token—
 * pedía token fresco a Firebase, `getIdToken` fallaba con
 * `FirebaseNetworkException`, y esa excepción salía de `intercept` por el
 * `runBlocking`. `Interceptor.intercept` sólo puede lanzar `IOException`:
 * cualquier otro `Throwable` lo relanza OkHttp en su hilo de despacho, sin
 * nadie que lo atrape. Resultado medido en el logcat del Galaxy A25:
 *
 *     FATAL EXCEPTION: OkHttp Dispatcher
 *     com.google.firebase.FirebaseNetworkException: A network error ...
 *
 * Con PIDs distintos en cada crash: la app reabría, el sync de 30 s volvía a
 * disparar y volvía a morir. Un bucle, justo cuando el cobrador está sin señal
 * y acumulando capturas sin enviar.
 */
class BearerAuthInterceptorFalloDeIdentidadTest {

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

    private fun clientWith(
        provider: FakeAuthTokenProvider,
        dispatcher: Dispatcher? = null
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BearerAuthInterceptor(provider))
        .apply { dispatcher?.let { dispatcher(it) } }
        .build()

    private fun get(client: OkHttpClient) {
        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
    }

    // ── El contrato: lo que sale de intercept es SIEMPRE una IOException ────

    @Test
    fun `si el proveedor de identidad falla, sale como IOException`() {
        val provider = FakeAuthTokenProvider(
            initialToken = "tok-viejo",
            fallaNormal = ProveedorDeIdentidadCaido()
        )

        try {
            get(clientWith(provider))
            fail("debía fallar la llamada: sin token no hay request que valga")
        } catch (e: IOException) {
            assertTrue(
                "la IOException debe conservar la causa para poder diagnosticar",
                e.cause is ProveedorDeIdentidadCaido
            )
        }
    }

    @Test
    fun `si falla la renovación tras un 401, tambien sale como IOException`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED))
        val provider = FakeAuthTokenProvider(
            initialToken = "tok-viejo",
            fallaRefresh = ProveedorDeIdentidadCaido()
        )

        try {
            get(clientWith(provider))
            fail("debía fallar: el 401 pidió renovar y el proveedor no contestó")
        } catch (e: IOException) {
            assertTrue(e.cause is ProveedorDeIdentidadCaido)
        }
        assertEquals(1, provider.refreshCalls)
    }

    @Test
    fun `el fallo del proveedor NO se disfraza de request sin Authorization`() {
        // Tragarse la excepción y seguir sin header confundiría "no te puedo
        // alcanzar" con "no hay sesión": el backend respondería 401 y eso puede
        // disparar el cierre de sesión de alguien que sólo estaba sin señal.
        val provider = FakeAuthTokenProvider(
            initialToken = "tok-viejo",
            fallaNormal = ProveedorDeIdentidadCaido()
        )

        runCatching { get(clientWith(provider)) }

        assertEquals(
            "el servidor no debió recibir ninguna request",
            0,
            server.requestCount
        )
    }

    // ── La reproducción del crash: el hilo de despacho de OkHttp ───────────

    @Test
    fun `en una llamada asincrona el hilo de despacho no muere`() {
        // Ésta es la prueba del incidente. OkHttp, ante un Throwable que no es
        // IOException, avisa al callback Y ADEMÁS lo relanza en su hilo — que
        // es lo que Android reporta como FATAL EXCEPTION y mata el proceso.
        // Un handler de excepciones no atrapadas en ese hilo es la única forma
        // de verlo desde una prueba de JVM.
        val noAtrapadas = AtomicReference<Throwable?>(null)
        val ejecutor = ThreadPoolExecutor(
            0,
            Int.MAX_VALUE,
            60,
            TimeUnit.SECONDS,
            SynchronousQueue()
        ) { runnable ->
            Thread(runnable, "OkHttp Dispatcher (prueba)").apply {
                setUncaughtExceptionHandler { _, t -> noAtrapadas.set(t) }
            }
        }
        val provider = FakeAuthTokenProvider(
            initialToken = "tok-viejo",
            fallaNormal = ProveedorDeIdentidadCaido()
        )
        val cliente = clientWith(provider, Dispatcher(ejecutor))

        val listo = CountDownLatch(1)
        val recibido = AtomicReference<IOException?>(null)
        cliente.newCall(Request.Builder().url(server.url("/")).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    recibido.set(e)
                    listo.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    listo.countDown()
                }
            })

        assertTrue("el callback nunca respondió", listo.await(10, TimeUnit.SECONDS))
        ejecutor.shutdown()
        ejecutor.awaitTermination(10, TimeUnit.SECONDS)

        assertNull(
            "nada debe escapar al hilo de despacho: eso es el FATAL EXCEPTION " +
                "que mataba la app (era ${noAtrapadas.get()})",
            noAtrapadas.get()
        )
        assertNotNull("el callback debe enterarse del fallo", recibido.get())
        assertTrue(recibido.get()?.cause is ProveedorDeIdentidadCaido)
    }

    // ── Los caminos sanos siguen igual ─────────────────────────────────────

    @Test
    fun `un proveedor sano sigue adjuntando el token`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK))
        val provider = FakeAuthTokenProvider(initialToken = "tok-123")

        get(clientWith(provider))

        assertEquals("Bearer tok-123", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `sin sesion sigue pasando sin header, que no es lo mismo que fallar`() {
        // `null` significa "no hay usuario"; la request sale sin Authorization y
        // el backend contesta 401 explícito. Ese camino NO debe convertirse en
        // excepción por culpa de este arreglo.
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED))
        val provider = FakeAuthTokenProvider(initialToken = null)

        get(clientWith(provider))

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals("un 401 sin token no se reintenta", 0, provider.refreshCalls)
    }

    @Test
    fun `la IOException del proveedor pasa tal cual, sin envolverse dos veces`() {
        // Si el proveedor ya falló con IOException (p. ej. su propia capa de
        // red), envolverla otra vez sólo entierra la causa real.
        val original = IOException("se cayó la red")
        val provider = FakeAuthTokenProvider(
            initialToken = "tok-viejo",
            fallaNormal = original
        )

        try {
            get(clientWith(provider))
            fail("debía fallar")
        } catch (e: IOException) {
            assertSame(original, e)
        }
    }
}
