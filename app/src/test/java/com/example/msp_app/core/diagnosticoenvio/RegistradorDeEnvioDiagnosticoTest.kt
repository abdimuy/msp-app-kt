package com.example.msp_app.core.diagnosticoenvio

import com.example.msp_app.core.database.dao.enviodiagnostico.EnvioDiagnosticoDao
import com.example.msp_app.core.network.diagnostico.EnvioObservado
import com.example.msp_app.core.network.diagnostico.FalloDeEnvio
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `E-APP-063 telemetria a lo mas una vez por fila y por error distinto`() = runBlocking {
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

    @Test
    fun `E-APP-064 registrar no bloquea aunque la base este trabada`() {
        val trabada = CountDownLatch(1)
        val r = registrador(dao = {
            trabada.await(5, TimeUnit.SECONDS)
            db.envioDiagnosticoDao()
        })
        val inicio = System.nanoTime()
        repeat(1_000) { r.registrar(EnvioObservado("pago", "p$it", red)) }
        val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio)
        trabada.countDown()
        assertTrue("1000 registros con la base trabada tardaron $ms ms", ms < 500)
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
