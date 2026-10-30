package com.example.msp_app.core.common.location.captura

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-142: simulación determinista del tomador anticipado. Reloj y proveedor falsos; el tiempo
 * es el virtual de `runTest`, así que un escenario "fix tarde a los 20 s" corre en microsegundos
 * y siempre igual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TomadorDeUbicacionTest {

    private class ProveedorFalso(
        var acceso: AccesoAUbicacion = AccesoAUbicacion.OK,
        var ultima: (suspend () -> FixDeUbicacion?)? = null
    ) : ProveedorDeUbicacion {
        val flujo = MutableSharedFlow<FixDeUbicacion>(extraBufferCapacity = 64)
        val suscripciones = AtomicInteger(0)
        var falloAlSuscribir: Throwable? = null

        override fun acceso() = acceso

        override suspend fun ultimaConocida(): FixDeUbicacion? = ultima?.invoke()

        override fun actualizaciones(): Flow<FixDeUbicacion> = callbackFlow {
            falloAlSuscribir?.let { throw it }
            suscripciones.incrementAndGet()
            val job = launch { flujo.collect { send(it) } }
            awaitClose {
                job.cancel()
                suscripciones.decrementAndGet()
            }
        }
    }

    private class ReporteFalso : ReporteDelTomador {
        val primeros = mutableListOf<Triple<Long, Int, Boolean>>()
        val sin = mutableListOf<MotivoSinUbicacion>()

        override fun primerFix(msDesdeInicio: Long, precisionM: Int, deUltimaConocida: Boolean) {
            primeros += Triple(msDesdeInicio, precisionM, deUltimaConocida)
        }

        override fun sinUbicacion(motivo: MotivoSinUbicacion) {
            sin += motivo
        }
    }

    private fun TestScope.reloj() = RelojMonotono { BASE + currentTime * 1_000_000L }

    private fun TestScope.fixAhora(
        precision: Double = 10.0,
        antiguedadMs: Long = 0,
        mock: Boolean = false
    ) = FixDeUbicacion(
        lat = 20.5888,
        lon = -100.3899,
        precisionM = precision,
        hora = Instant.parse("2026-10-30T18:00:00Z").plusMillis(currentTime - antiguedadMs),
        elapsedRealtimeNanos = BASE + (currentTime - antiguedadMs) * 1_000_000L,
        esMock = mock
    )

    private fun TestScope.tomador(
        p: ProveedorFalso,
        reporte: ReporteDelTomador = ReporteDelTomador.Nulo,
        ventanaMs: Long = 60_000
    ) = TomadorDeUbicacion(p, reloj(), ventanaMs = ventanaMs, reporte = reporte)

    @Test
    fun `E-APP-142 fix que llega antes de guardar queda disponible y se entrega al guardar`() =
        runTest {
            val p = ProveedorFalso()
            val r = ReporteFalso()
            val t = tomador(p, r)
            val job = t.iniciar(this)
            runCurrent()
            advanceTimeBy(5_000)
            val f = fixAhora()
            p.flujo.emit(f)
            runCurrent()
            assertEquals(EstadoDeUbicacion.Disponible(f), t.estado.value)
            advanceTimeBy(10_000)
            assertEquals(DecisionDeUbicacion.AceptarAntes(f), t.mejorUbicacionAntesDeGuardar())
            assertEquals(listOf(Triple(5_000L, 10, false)), r.primeros)
            job.cancel()
        }

    @Test
    fun `E-APP-142 la ultima conocida cuenta como primer fix y se marca`() = runTest {
        val p = ProveedorFalso()
        p.ultima = { fixAhora(antiguedadMs = 8_000) }
        val r = ReporteFalso()
        val t = tomador(p, r)
        val job = t.iniciar(this)
        runCurrent()
        assertTrue(t.estado.value is EstadoDeUbicacion.Disponible)
        assertEquals(listOf(Triple(0L, 10, true)), r.primeros)
        job.cancel()
    }

    @Test
    fun `E-APP-142 un fix tarde no se entrega a un guardado anterior a el`() = runTest {
        val p = ProveedorFalso()
        val t = tomador(p)
        val job = t.iniciar(this)
        runCurrent()
        val guardarEn = reloj().elapsedNanos()
        advanceTimeBy(20_000)
        val tarde = fixAhora()
        p.flujo.emit(tarde)
        runCurrent()
        // Disponible para guardados posteriores...
        assertEquals(DecisionDeUbicacion.AceptarAntes(tarde), t.mejorUbicacionAntesDeGuardar())
        // ...pero no para el guardado que ya ocurrio: eso es trabajo de la regla DESPUES (frente 5).
        assertEquals(
            DecisionDeUbicacion.Rechazar(MotivoDeRechazo.SIN_FIX),
            t.mejorUbicacionAntesDeGuardar(guardarEn)
        )
        job.cancel()
    }

    @Test
    fun `E-APP-142 si nunca llega un fix el estado pasa a SinFix al vencer la ventana y guardar no se bloquea`() =
        runTest {
            val p = ProveedorFalso()
            val r = ReporteFalso()
            val t = tomador(p, r, ventanaMs = 30_000)
            val job = t.iniciar(this)
            runCurrent()
            assertEquals(EstadoDeUbicacion.Esperando, t.estado.value)
            // Guardar durante la espera responde al instante, sin avanzar el tiempo virtual.
            val antes = currentTime
            assertEquals(
                DecisionDeUbicacion.Rechazar(MotivoDeRechazo.SIN_FIX),
                t.mejorUbicacionAntesDeGuardar()
            )
            assertEquals(antes, currentTime)
            advanceTimeBy(30_001)
            runCurrent()
            assertEquals(EstadoDeUbicacion.SinFix, t.estado.value)
            assertEquals(listOf(MotivoSinUbicacion.SIN_FIX), r.sin)
            assertTrue(job.isCompleted)
            assertEquals(0, p.suscripciones.get())
        }

    @Test
    fun `E-APP-142 permiso negado no pide nada y reporta SinPermiso`() = runTest {
        val p = ProveedorFalso(acceso = AccesoAUbicacion.SIN_PERMISO)
        val r = ReporteFalso()
        val t = tomador(p, r)
        val job = t.iniciar(this)
        runCurrent()
        assertEquals(EstadoDeUbicacion.SinPermiso, t.estado.value)
        assertEquals(listOf(MotivoSinUbicacion.SIN_PERMISO), r.sin)
        assertEquals(0, p.suscripciones.get())
        assertTrue(job.isCompleted)
        assertTrue(t.mejorUbicacionAntesDeGuardar() is DecisionDeUbicacion.Rechazar)
    }

    @Test
    fun `E-APP-142 GPS apagado reporta GpsApagado`() = runTest {
        val p = ProveedorFalso(acceso = AccesoAUbicacion.GPS_APAGADO)
        val r = ReporteFalso()
        val t = tomador(p, r)
        t.iniciar(this)
        runCurrent()
        assertEquals(EstadoDeUbicacion.GpsApagado, t.estado.value)
        assertEquals(listOf(MotivoSinUbicacion.GPS_APAGADO), r.sin)
    }

    @Test
    fun `E-APP-142 SecurityException al pedir actualizaciones se vuelve SinPermiso sin propagarse`() =
        runTest {
            val p = ProveedorFalso()
            p.falloAlSuscribir = SecurityException("revocado")
            val t = tomador(p)
            val job = t.iniciar(this)
            runCurrent()
            assertEquals(EstadoDeUbicacion.SinPermiso, t.estado.value)
            assertTrue(job.isCompleted && !job.isCancelled)
        }

    @Test
    fun `E-APP-142 un error del proveedor se vuelve SinFix con motivo ERROR y la ultima conocida que falla no estorba`() =
        runTest {
            val p = ProveedorFalso()
            p.ultima = { error("play services caido") }
            p.falloAlSuscribir = IllegalStateException("caido")
            val r = ReporteFalso()
            val t = tomador(p, r)
            t.iniciar(this)
            runCurrent()
            assertEquals(EstadoDeUbicacion.SinFix, t.estado.value)
            assertEquals(listOf(MotivoSinUbicacion.ERROR), r.sin)
        }

    @Test
    fun `E-APP-142 los fixes simulados o imprecisos no vuelven Disponible el estado`() = runTest {
        val p = ProveedorFalso()
        val t = tomador(p)
        val job = t.iniciar(this)
        runCurrent()
        p.flujo.emit(fixAhora(mock = true))
        p.flujo.emit(fixAhora(precision = 300.0))
        runCurrent()
        assertEquals(EstadoDeUbicacion.Esperando, t.estado.value)
        p.flujo.emit(fixAhora(precision = 12.0))
        runCurrent()
        assertTrue(t.estado.value is EstadoDeUbicacion.Disponible)
        job.cancel()
    }

    @Test
    fun `E-APP-142 al guardar un fix viejo se rechaza por edad aunque el estado siga Disponible`() =
        runTest {
            val p = ProveedorFalso()
            val t = tomador(p)
            val job = t.iniciar(this)
            runCurrent()
            p.flujo.emit(fixAhora())
            runCurrent()
            advanceTimeBy(31_000)
            assertEquals(
                DecisionDeUbicacion.Rechazar(MotivoDeRechazo.EDAD_EXCEDIDA),
                t.mejorUbicacionAntesDeGuardar()
            )
            job.cancel()
        }

    @Test
    fun `E-APP-142 proceso muerto o pantalla cerrada la cancelacion no deja trabajo colgado`() =
        runTest {
            val p = ProveedorFalso()
            val pantalla = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
            val t = tomador(p)
            val job = t.iniciar(pantalla)
            runCurrent()
            assertEquals(1, p.suscripciones.get())
            pantalla.coroutineContext[Job]!!.cancel(CancellationException("pantalla cerrada"))
            runCurrent()
            assertTrue(job.isCancelled)
            assertEquals(0, p.suscripciones.get())
            // Un fix posterior ya no llega a ningun lado.
            p.flujo.emit(fixAhora())
            runCurrent()
            assertTrue(t.mejorUbicacionAntesDeGuardar() is DecisionDeUbicacion.Rechazar)
        }

    @Test
    fun `E-APP-142 iniciar de nuevo reemplaza la colección anterior y conserva el buffer`() =
        runTest {
            val p = ProveedorFalso()
            val t = tomador(p)
            val j1 = t.iniciar(this)
            runCurrent()
            p.flujo.emit(fixAhora(precision = 7.0))
            runCurrent()
            val j2 = t.iniciar(this)
            runCurrent()
            assertTrue(j1.isCancelled)
            assertEquals(1, p.suscripciones.get())
            assertEquals(7.0, t.mejorUbicacionAntesDeGuardar().precisionM!!, 0.0)
            t.detener()
            runCurrent()
            assertTrue(j2.isCancelled)
            assertEquals(0, p.suscripciones.get())
        }

    @Test
    fun `E-APP-142 detener sin haber iniciado no hace nada y el flujo vacio termina en SinFix`() =
        runTest {
            val p = object : ProveedorDeUbicacion {
                override fun acceso() = AccesoAUbicacion.OK

                override suspend fun ultimaConocida(): FixDeUbicacion? = null

                override fun actualizaciones(): Flow<FixDeUbicacion> = emptyFlow()
            }
            val t = TomadorDeUbicacion(p, reloj())
            t.detener()
            t.iniciar(this)
            advanceUntilIdle()
            assertEquals(EstadoDeUbicacion.SinFix, t.estado.value)
        }

    @Test
    fun `E-APP-142 el buffer acota su tamano y descarta los mas viejos`() = runTest {
        val p = ProveedorFalso()
        val t = tomador(p, ventanaMs = 10_000_000)
        val job = t.iniciar(this)
        runCurrent()
        val inicio = reloj().elapsedNanos()
        repeat(TomadorDeUbicacion.CAPACIDAD + 8) {
            advanceTimeBy(100)
            p.flujo.emit(fixAhora(precision = 5.0 + it % 3))
            runCurrent()
        }
        // Los 8 primeros ya se descartaron: para un guardado a los 500 ms no queda ningun fix previo.
        assertEquals(
            DecisionDeUbicacion.Rechazar(MotivoDeRechazo.SIN_FIX),
            t.mejorUbicacionAntesDeGuardar(inicio + 500_000_000L)
        )
        // Los mas recientes si se conservan.
        assertTrue(t.mejorUbicacionAntesDeGuardar() is DecisionDeUbicacion.AceptarAntes)
        job.cancel()
    }

    @Test
    fun `E-APP-142 el primer fix se reporta una sola vez`() = runTest {
        val p = ProveedorFalso()
        val r = ReporteFalso()
        val t = tomador(p, r)
        val job = t.iniciar(this)
        runCurrent()
        repeat(3) {
            advanceTimeBy(1_000)
            p.flujo.emit(fixAhora())
            runCurrent()
        }
        assertEquals(1, r.primeros.size)
        job.cancel()
    }

    @Test
    fun `E-APP-142 la ventana de tiempo debe ser positiva`() {
        val p = ProveedorFalso()
        var lanzo = false
        try {
            TomadorDeUbicacion(p, RelojMonotono { 0L }, ventanaMs = 0)
        } catch (_: IllegalArgumentException) {
            lanzo = true
        }
        assertTrue(lanzo)
    }

    private companion object {
        const val BASE = 5_000_000_000_000L
    }
}
