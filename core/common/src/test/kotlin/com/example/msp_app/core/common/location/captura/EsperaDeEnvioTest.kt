package com.example.msp_app.core.common.location.captura

import com.example.msp_app.core.common.time.AppClock
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-147 (K3, K4): la retencion del envio, con tiempo virtual, reloj de pared y reloj monotono
 * falsos. t = 0 es el guardado (la pared marca `GUARDADO_MS`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EsperaDeEnvioTest {

    /** Reloj de pared que el test mueve a mano, independiente del tiempo virtual. */
    private class Pared(var ms: Long) : AppClock {
        override fun now(): Instant = Instant.ofEpochMilli(ms)
    }

    private fun TestScope.monotono() = RelojMonotono { BASE + currentTime * NANOS_POR_MS }

    private fun TestScope.espera(pared: Pared) = EsperaDeEnvio(pared, monotono())

    /** Una "fila" en Room que el test llena en el instante que quiera. */
    private class Fila {
        @Volatile var tiene = false
        var lecturas = 0
        suspend fun leer(): Boolean {
            lecturas++
            return tiene
        }
    }

    @Test
    fun `E-APP-147 fix antes de guardar - la captura ya trae ubicacion y no espera nada`() =
        runTest {
            val fila = Fila().apply { tiene = true }
            val salida = espera(Pared(GUARDADO_MS)).esperar(GUARDADO_MS, 30_000, fila::leer)
            assertEquals(SalidaDeLaEspera.HAY_UBICACION, salida)
            assertEquals(0L, currentTime)
        }

    @Test
    fun `E-APP-147 fix durante la espera - sale de inmediato`() = runTest {
        val fila = Fila()
        val pared = Pared(GUARDADO_MS)
        var salida: SalidaDeLaEspera? = null
        val job = launch { salida = espera(pared).esperar(GUARDADO_MS, 30_000, fila::leer) }
        runCurrent()
        advanceTimeBy(7_000)
        assertEquals("sigue esperando", null, salida)

        fila.tiene = true
        advanceTimeBy(250)
        runCurrent()

        assertEquals(SalidaDeLaEspera.HAY_UBICACION, salida)
        assertTrue("salio en cuanto se leyo, no a los 30 s: t=$currentTime", currentTime <= 7_250)
        job.cancel()
    }

    @Test
    fun `E-APP-147 nunca llega - sale a los 30 s exactos`() = runTest {
        val fila = Fila()
        var salida: SalidaDeLaEspera? = null
        launch { salida = espera(Pared(GUARDADO_MS)).esperar(GUARDADO_MS, 30_000, fila::leer) }
        runCurrent()
        advanceTimeBy(29_999)
        assertEquals(null, salida)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SalidaDeLaEspera.VENCIO, salida)
        assertEquals(30_000L, currentTime)
    }

    @Test
    fun `E-APP-147 la toma ya dijo permiso negado o GPS apagado - no espera`() = runTest {
        val fila = Fila()
        val salida = espera(Pared(GUARDADO_MS)).esperar(
            GUARDADO_MS,
            30_000,
            fila::leer,
            noVaALlegar = { true }
        )
        assertEquals(SalidaDeLaEspera.NO_VA_A_LLEGAR, salida)
        assertEquals("ni un milisegundo", 0L, currentTime)
    }

    @Test
    fun `E-APP-147 si la toma lo dice durante la espera, deja de esperar`() = runTest {
        val fila = Fila()
        var imposible = false
        var salida: SalidaDeLaEspera? = null
        launch {
            salida = espera(Pared(GUARDADO_MS)).esperar(
                GUARDADO_MS,
                30_000,
                fila::leer,
                noVaALlegar = { imposible }
            )
        }
        runCurrent()
        advanceTimeBy(3_000)
        imposible = true
        advanceTimeBy(250)
        runCurrent()
        assertEquals(SalidaDeLaEspera.NO_VA_A_LLEGAR, salida)
    }

    // ─── K4 · el reloj de pared no manda ──────────────────────────────────────

    @Test
    fun `E-APP-147 reloj de pared atrasado 1 h - no retiene 1 h, no espera`() = runTest {
        val fila = Fila()
        // La pared marca 1 h MENOS que al guardar: el plazo "guardado + 30 s" queda 1 h y 30 s adelante.
        val pared = Pared(GUARDADO_MS - 3_600_000)
        val salida = espera(pared).esperar(GUARDADO_MS, 30_000, fila::leer)
        assertEquals(SalidaDeLaEspera.SIN_ESPERA, salida)
        assertEquals(0L, currentTime)
        assertEquals("ni siquiera leyo Room", 0, fila.lecturas)
    }

    @Test
    fun `E-APP-147 reloj de pared adelantado - el plazo ya vencio y no retiene`() = runTest {
        val fila = Fila()
        val pared = Pared(GUARDADO_MS + 3_600_000)
        assertEquals(
            SalidaDeLaEspera.SIN_ESPERA,
            espera(pared).esperar(GUARDADO_MS, 30_000, fila::leer)
        )
        assertEquals(0L, currentTime)
    }

    @Test
    fun `E-APP-147 la pared salta 1 h atras DURANTE la espera - manda el monotono`() = runTest {
        val fila = Fila()
        val pared = Pared(GUARDADO_MS)
        var salida: SalidaDeLaEspera? = null
        launch { salida = espera(pared).esperar(GUARDADO_MS, 30_000, fila::leer) }
        runCurrent()
        advanceTimeBy(10_000)
        pared.ms -= 3_600_000
        advanceTimeBy(19_999)
        assertEquals(null, salida)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SalidaDeLaEspera.VENCIO, salida)
        assertEquals("30 s en total, ni mas ni menos", 30_000L, currentTime)
    }

    @Test
    fun `E-APP-147 reanudar tras una parada espera solo lo que queda`() = runTest {
        val fila = Fila()
        val pared = Pared(GUARDADO_MS + 10_000) // la corrida arranca a +10 s del guardado
        var salida: SalidaDeLaEspera? = null
        launch { salida = espera(pared).esperar(GUARDADO_MS, 30_000, fila::leer) }
        runCurrent()
        advanceTimeBy(19_999)
        assertEquals(null, salida)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SalidaDeLaEspera.VENCIO, salida)
        assertEquals("20 s, no 30", 20_000L, currentTime)
    }

    @Test
    fun `E-APP-147 un plazo guardado mayor a 30 s nunca retiene mas de 30 s`() = runTest {
        val fila = Fila()
        val salida = espera(Pared(GUARDADO_MS)).esperar(GUARDADO_MS, 3_600_000, fila::leer)
        assertEquals("el restante (1 h) excede el tope", SalidaDeLaEspera.SIN_ESPERA, salida)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `E-APP-147 plazo cero o negativo no espera`() = runTest {
        val fila = Fila()
        assertEquals(
            SalidaDeLaEspera.SIN_ESPERA,
            espera(Pared(GUARDADO_MS)).esperar(GUARDADO_MS, 0, fila::leer)
        )
        assertEquals(
            SalidaDeLaEspera.SIN_ESPERA,
            espera(Pared(GUARDADO_MS)).esperar(GUARDADO_MS, -5, fila::leer)
        )
    }

    // ─── K3 · cancelacion y lectura ───────────────────────────────────────────

    @Test
    fun `E-APP-147 la cancelacion se propaga y no deja trabajo vivo`() = runTest {
        val fila = Fila()
        var terminoNormal = false
        var cancelado: CancellationException? = null
        val job: Job = launch {
            try {
                espera(Pared(GUARDADO_MS)).esperar(GUARDADO_MS, 30_000, fila::leer)
                terminoNormal = true
            } catch (e: CancellationException) {
                cancelado = e
                throw e
            }
        }
        runCurrent()
        advanceTimeBy(5_000)
        job.cancel()
        runCurrent()
        assertFalse("no volvio como si hubiera vencido", terminoNormal)
        assertTrue("la cancelacion llego intacta", cancelado != null)
        assertTrue(job.isCancelled)
        val lecturas = fila.lecturas
        advanceTimeBy(60_000)
        assertEquals("no siguio leyendo tras cancelar", lecturas, fila.lecturas)
    }

    @Test
    fun `E-APP-147 una cancelacion dentro de la lectura tambien se propaga`() = runTest {
        var cancelado = false
        val job = launch {
            try {
                espera(Pared(GUARDADO_MS)).esperar(
                    GUARDADO_MS,
                    30_000,
                    hayUbicacion = { throw CancellationException("cancelado en Room") }
                )
            } catch (e: CancellationException) {
                cancelado = true
                throw e
            }
        }
        runCurrent()
        assertTrue(cancelado)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `E-APP-147 si Room falla al leer, no espera mas y lo dice`() = runTest {
        val salida = espera(Pared(GUARDADO_MS)).esperar(
            GUARDADO_MS,
            30_000,
            hayUbicacion = { error("Room cerrado") }
        )
        assertEquals(SalidaDeLaEspera.LECTURA_FALLIDA, salida)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `E-APP-147 propiedad - nunca se espera mas de 30 s sea cual sea el reloj de pared`() {
        val azar = Random(147)
        var esperadas = 0
        var sinEsperar = 0
        repeat(ITERACIONES) {
            runTest {
                val desfase =
                    if (azar.nextBoolean()) {
                        azar.nextLong(
                            0,
                            30_000
                        )
                    } else {
                        azar.nextLong(-7_200_000, 7_200_000)
                    }
                val plazo = if (azar.nextInt(4) > 0) 30_000L else azar.nextLong(-1_000, 60_000)
                val pared = Pared(GUARDADO_MS + desfase)
                val fila = Fila()
                val antes = currentTime
                val salida = espera(pared).esperar(GUARDADO_MS, plazo, fila::leer)
                val gastado = currentTime - antes
                assertTrue(
                    "gasto $gastado ms con desfase $desfase y plazo $plazo",
                    gastado <= 30_000
                )
                if (gastado > 0) esperadas++ else sinEsperar++
                if (salida == SalidaDeLaEspera.SIN_ESPERA) assertEquals(0L, gastado)
            }
        }
        // Control positivo: el generador produce corridas que esperan Y corridas que no.
        assertTrue("hubo esperas ($esperadas)", esperadas > 50)
        assertTrue("hubo corridas sin espera ($sinEsperar)", sinEsperar > 50)
    }

    private companion object {
        const val GUARDADO_MS = 1_793_383_200_000L
        const val BASE = 9_000_000_000_000L
        const val NANOS_POR_MS = 1_000_000L
        const val ITERACIONES = 400
    }
}
