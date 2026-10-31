package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.location.captura.AccesoAUbicacion
import com.example.msp_app.core.common.location.captura.FixDeUbicacion
import com.example.msp_app.core.common.location.captura.ProveedorDeUbicacion
import com.example.msp_app.core.common.location.captura.RelojMonotono
import com.example.msp_app.core.common.location.captura.TomadorDeUbicacion
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * E-APP-148 (K6): el puerto de ubicacion de las visitas contesta lo que el tomador ya tiene, segun
 * la regla (<= 30 m, no simulada, fresca), sin suspender ni esperar al GPS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UbicacionDeVisitaAdapterTest {

    private class Proveedor : ProveedorDeUbicacion {
        val flujo = MutableSharedFlow<FixDeUbicacion>(extraBufferCapacity = 8)

        override fun acceso() = AccesoAUbicacion.OK

        override suspend fun ultimaConocida(): FixDeUbicacion? = null

        override fun actualizaciones(): Flow<FixDeUbicacion> = callbackFlow {
            val job = launch { flujo.collect { send(it) } }
            awaitClose { job.cancel() }
        }
    }

    private fun fix(ms: Long, precision: Double = 10.0, mock: Boolean = false) = FixDeUbicacion(
        lat = 20.5888,
        lon = -100.3899,
        precisionM = precision,
        hora = Instant.parse("2026-10-30T18:00:00Z").plusMillis(ms),
        elapsedRealtimeNanos = BASE + ms * 1_000_000L,
        esMock = mock
    )

    @Test
    fun `E-APP-148 contesta la ubicacion que el tomador ya tiene y no espera`() = runTest {
        val p = Proveedor()
        val t = TomadorDeUbicacion(p, RelojMonotono { BASE + currentTime * 1_000_000L })
        val job = t.iniciar(this)
        runCurrent()
        advanceTimeBy(4_000)
        p.flujo.emit(fix(4_000))
        runCurrent()

        val antes = currentTime
        val donde = UbicacionDeVisitaAdapter(t).ubicacionActual()

        assertEquals(20.5888, donde!!.lat, 0.0)
        assertEquals(-100.3899, donde.lng, 0.0)
        assertEquals("no espera", antes, currentTime)
        job.cancel()
    }

    @Test
    fun `E-APP-148 sin fix, con fix impreciso o simulado contesta null`() = runTest {
        val p = Proveedor()
        val t = TomadorDeUbicacion(p, RelojMonotono { BASE + currentTime * 1_000_000L })
        val job = t.iniciar(this)
        runCurrent()
        assertNull("sin fix", UbicacionDeVisitaAdapter(t).ubicacionActual())

        advanceTimeBy(2_000)
        p.flujo.emit(fix(2_000, precision = 80.0))
        p.flujo.emit(fix(2_000, mock = true))
        runCurrent()
        assertNull("impreciso o simulado", UbicacionDeVisitaAdapter(t).ubicacionActual())
        job.cancel()
    }

    private companion object {
        const val BASE = 5_000_000_000_000L
    }
}
