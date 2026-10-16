package com.example.msp_app.core.diagnosticoenvio

import com.example.msp_app.core.database.dao.enviodiagnostico.EnvioDiagnosticoDao
import com.example.msp_app.core.network.diagnostico.EnvioObservado
import com.example.msp_app.core.network.diagnostico.FalloDeEnvio
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** E-APP-063: el registrador escribe, deduplica la telemetría, no bloquea y no lanza. */
class RegistradorDeEnvioDiagnosticoTest : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-10-16T18:00:00.789Z"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reportes = CopyOnWriteArrayList<Map<String, Any>>()

    @After
    fun tearDownScope() {
        scope.cancel()
    }

    private fun registrador(dao: () -> EnvioDiagnosticoDao = { db.envioDiagnosticoDao() }) =
        RegistradorDeEnvioDiagnostico(dao = dao, clock = clock, reportar = {
            reportes += it
        }, scope = scope)

    private val red = FalloDeEnvio("network_error", "Sin conexión", 0)
    private val http502 = FalloDeEnvio("http_502", "Falla del servidor", 502)

    @Test
    fun `escribe el fallo con instante truncado a segundos y reporta solo id, codigo y http`() =
        runBlocking {
            registrador().escribir(EnvioObservado("pago", "p1", red))
            val fila = db.envioDiagnosticoDao().obtener("pago", "p1")!!
            assertEquals("network_error", fila.ULTIMO_ERROR_CODIGO)
            assertEquals("Sin conexión", fila.ULTIMO_ERROR_MENSAJE)
            assertEquals(0, fila.ULTIMO_ERROR_HTTP)
            assertEquals("2026-10-16T18:00:00Z", fila.ULTIMO_INTENTO_AT)
            assertEquals(
                listOf(
                    mapOf<String, Any>(
                        "tipo" to "pago",
                        "fila_id" to "p1",
                        "codigo" to "network_error",
                        "http" to 0
                    )
                ),
                reportes.toList()
            )
        }

    @Test
    fun `E-APP-063 telemetria a lo mas una vez por fila y codigo`() = runBlocking {
        val r = registrador()
        repeat(5) { r.escribir(EnvioObservado("pago", "p1", red)) }
        r.escribir(EnvioObservado("pago", "p1", http502))
        r.escribir(EnvioObservado("pago", "p1", http502))
        r.escribir(EnvioObservado("visita", "p1", red))
        assertEquals(
            listOf("network_error", "http_502", "network_error"),
            reportes.map { it["codigo"] }
        )
        assertEquals(7, db.envioDiagnosticoDao().obtener("pago", "p1")!!.INTENTOS)
    }

    @Test
    fun `el exito limpia el error`() = runBlocking {
        val r = registrador()
        r.escribir(EnvioObservado("visita", "v1", red))
        r.escribir(EnvioObservado("visita", "v1", null))
        val fila = db.envioDiagnosticoDao().obtener("visita", "v1")!!
        assertNull(fila.ULTIMO_ERROR_CODIGO)
        assertEquals("2026-10-16T18:00:00Z", fila.ULTIMO_EXITO_AT)
    }

    @Test
    fun `una base que revienta y una telemetria que revienta no lanzan`() = runBlocking {
        RegistradorDeEnvioDiagnostico(dao = {
            throw IllegalStateException("base caída")
        }, clock = clock, scope = scope)
            .escribir(EnvioObservado("pago", "p1", red))
        RegistradorDeEnvioDiagnostico(
            dao = { db.envioDiagnosticoDao() },
            clock = clock,
            reportar = { throw IllegalStateException("firestore caído") },
            scope = scope
        ).escribir(EnvioObservado("pago", "p2", red))
        assertEquals(
            "network_error",
            db.envioDiagnosticoDao().obtener("pago", "p2")!!.ULTIMO_ERROR_CODIGO
        )
    }

    /**
     * Si `registrar` llegara a escribir en el hilo que llama (regresión), la
     * primera llamada se quedaría 1 s en la base trabada y el `get` de 2 s
     * vencería: la prueba se pone ROJA, no se cuelga.
     */
    @Test(timeout = 10_000)
    fun `E-APP-064 registrar no bloquea aunque la base este trabada`() {
        val trabada = CountDownLatch(1)
        val r = registrador(dao = {
            trabada.await(1, TimeUnit.SECONDS)
            db.envioDiagnosticoDao()
        })
        val hilo = Executors.newSingleThreadExecutor()
        try {
            val tarea = hilo.submit {
                repeat(
                    1_000
                ) { r.registrar(EnvioObservado("pago", "p$it", red)) }
            }
            try {
                tarea.get(2, TimeUnit.SECONDS)
            } catch (_: TimeoutException) {
                throw AssertionError("registrar bloqueó al hilo que llama con la base trabada")
            }
        } finally {
            trabada.countDown()
            hilo.shutdownNow()
        }
    }

    @Test
    fun `E-APP-063 poda a lo mas una vez al dia las filas sin intentos en 30 dias`() = runBlocking {
        val dao = db.envioDiagnosticoDao()
        val r = registrador()
        clock.setNow(Instant.parse("2026-08-01T10:00:00Z"))
        r.escribir(
            EnvioObservado("pago", "soltada", red)
        ) // primera escritura: poda (nada que borrar)
        assertEquals("soltada", dao.obtener("pago", "soltada")!!.FILA_ID)

        clock.setNow(Instant.parse("2026-09-01T10:00:00Z")) // +31 días
        r.escribir(EnvioObservado("pago", "otra", red))
        assertNull("sin intentos en 30 días: podada", dao.obtener("pago", "soltada"))

        // Una fila vieja que aparece después de la poda de hoy no se borra hasta mañana.
        dao.registrarFallo("pago", "vieja", "timeout", "Tiempo agotado", 0, "2026-07-01T00:00:00Z")
        clock.setNow(Instant.parse("2026-09-01T20:00:00Z"))
        r.escribir(EnvioObservado("pago", "otra", red))
        assertEquals(
            "misma fecha: no vuelve a podar",
            "vieja",
            dao.obtener("pago", "vieja")!!.FILA_ID
        )
        clock.setNow(Instant.parse("2026-09-02T10:00:01Z"))
        r.escribir(EnvioObservado("pago", "otra", null))
        assertNull("al día siguiente sí", dao.obtener("pago", "vieja"))
        assertEquals("la que sigue activa se queda", "otra", dao.obtener("pago", "otra")!!.FILA_ID)
    }

    @Test
    fun `registrar escribe en orden de llegada`() {
        val r = registrador()
        r.registrar(EnvioObservado("pago", "p1", red))
        r.registrar(EnvioObservado("pago", "p1", http502))
        r.registrar(EnvioObservado("pago", "p1", null))
        repeat(200) {
            val fila = runBlocking { db.envioDiagnosticoDao().obtener("pago", "p1") }
            if (fila?.INTENTOS == 3) {
                assertNull("el éxito llegó al final y limpió", fila.ULTIMO_ERROR_CODIGO)
                return
            }
            Thread.sleep(10)
        }
        throw AssertionError("no se escribieron los tres en orden")
    }
}
