package com.example.msp_app.core.common.location.captura

import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-149: arreglos de la revision independiente del frente 3 (H3, H4, H5).
 * Casos del revisor (rev-frente3-pruebas) hechos permanentes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RevisionFrente3Test {
    private val s = 1_000_000_000L

    private fun fix(
        t: Long,
        p: Double = 10.0,
        lat: Double = 20.0,
        lon: Double = -100.0,
        v: Double? = null
    ) = FixDeUbicacion(lat, lon, p, Instant.EPOCH, t, v, false)

    private fun dec(g: Long, vararg f: FixDeUbicacion) =
        AceptacionDeUbicacion.decidir(g, f.toList(), ConfigDeAceptacion.POR_DEFECTO)

    // ---- H3 -------------------------------------------------------------------------------

    @Test
    fun `E-APP-149 H3 una referencia previa de 400 m no esconde 166 m de movimiento`() {
        val ref = fix(90 * s, p = 400.0, lat = 20.0)
        val cand = fix(110 * s, p = 10.0, lat = 20.0 + 0.0015)
        assertEquals(
            DecisionDeUbicacion.Rechazar(MotivoDeRechazo.CON_MOVIMIENTO),
            dec(100 * s, ref, cand)
        )
    }

    @Test
    fun `E-APP-149 H3 control positivo con referencia precisa y sin movimiento acepta despues`() {
        val ref = fix(90 * s, p = 400.0, lat = 20.0)
        val cand = fix(110 * s, p = 10.0, lat = 20.0 + 0.00005) // ~5 m
        assertTrue(dec(100 * s, ref, cand) is DecisionDeUbicacion.AceptarDespues)
    }

    @Test
    fun `E-APP-149 H3 propiedad una referencia mas imprecisa que el maximo se trata como el maximo`() {
        val rnd = Random(149)
        repeat(1000) {
            val norteM = rnd.nextDouble(0.0, 400.0)
            val lat = 20.0 + norteM / 111_194.9
            val cand = fix(110 * s, p = rnd.nextDouble(1.0, 29.0), lat = lat)
            val gigante = fix(90 * s, p = rnd.nextDouble(30.0, 5000.0), lat = 20.0)
            val tope = fix(90 * s, p = 30.0, lat = 20.0)
            assertEquals(dec(100 * s, tope, cand), dec(100 * s, gigante, cand))
            // Ante la duda, rechazar: mas alla de lo que el tope de ambas precisiones explica, es movimiento.
            val explicable = kotlin.math.sqrt(30.0 * 30.0 + cand.precisionM * cand.precisionM)
            if (norteM > explicable + 1.0 && norteM > 15.0) {
                assertEquals(
                    DecisionDeUbicacion.Rechazar(MotivoDeRechazo.CON_MOVIMIENTO),
                    dec(100 * s, gigante, cand)
                )
            }
        }
    }

    // ---- H5 -------------------------------------------------------------------------------

    @Test
    fun `E-APP-149 H5 la config remota extrema se rechaza y conserva el vigente`() {
        val vigente = ConfigDeAceptacion()
        val c = vigente.actualizadaCon(
            mapOf(
                "max_edad_seg" to 600L,
                "max_precision_m" to 500.0,
                "min_movimiento_m" to 200.0,
                "max_velocidad_ms" to 10.0
            )
        )
        assertEquals(vigente, c)
        val v2 = ConfigDeAceptacion(45, 25.0, 10.0, 2.0)
        val todas = mapOf(
            "max_edad_seg" to 61L,
            "max_precision_m" to 50.1,
            "min_movimiento_m" to 50.1,
            "max_velocidad_ms" to 3.1
        )
        assertEquals(v2, v2.actualizadaCon(todas))
    }

    @Test
    fun `E-APP-149 H5 los topes son 60 s 50 m 50 m y 3 m por s inclusivos`() {
        fun con(k: String, v: Any) = ConfigDeAceptacion().actualizadaCon(mapOf(k to v))
        assertEquals(60L, con("max_edad_seg", 60L).maxEdadSeg)
        assertEquals(30L, con("max_edad_seg", 61L).maxEdadSeg)
        assertEquals(50.0, con("max_precision_m", 50.0).maxPrecisionM, 0.0)
        assertEquals(30.0, con("max_precision_m", 50.01).maxPrecisionM, 0.0)
        assertEquals(50.0, con("min_movimiento_m", 50.0).minMovimientoM, 0.0)
        assertEquals(15.0, con("min_movimiento_m", 50.01).minMovimientoM, 0.0)
        assertEquals(3.0, con("max_velocidad_ms", 3.0).maxVelocidadMs, 0.0)
        assertEquals(1.0, con("max_velocidad_ms", 3.01).maxVelocidadMs, 0.0)
    }

    // ---- H4 -------------------------------------------------------------------------------

    private class Prov(val ultima: suspend () -> FixDeUbicacion?) : ProveedorDeUbicacion {
        var susc = 0
        val canal = MutableSharedFlow<FixDeUbicacion>(extraBufferCapacity = 8)
        override fun acceso() = AccesoAUbicacion.OK
        override suspend fun ultimaConocida() = ultima()
        override fun actualizaciones(): Flow<FixDeUbicacion> = callbackFlow {
            susc++
            val j = launch { canal.collect { send(it) } }
            awaitClose {
                j.cancel()
                susc--
            }
        }
    }

    @Test
    fun `E-APP-149 H4 una ultima conocida colgada no impide suscribirse ni recibir fixes nuevos`() =
        runTest {
            val p = Prov { CompletableDeferred<FixDeUbicacion?>().await() }
            val t = TomadorDeUbicacion(p, RelojMonotono { currentTime * 1_000_000L })
            val job = t.iniciar(this)
            runCurrent()
            assertEquals(1, p.susc)
            advanceTimeBy(1_000)
            p.canal.emit(fix(currentTime * 1_000_000L))
            runCurrent()
            assertTrue(t.estado.value is EstadoDeUbicacion.Disponible)
            job.cancel()
            runCurrent()
            assertEquals(0, p.susc)
        }

    @Test
    fun `E-APP-149 H4 la ultima conocida lenta pero valida tambien llega`() = runTest {
        val p = Prov {
            kotlinx.coroutines.delay(2_000)
            fix(currentTime * 1_000_000L)
        }
        val t = TomadorDeUbicacion(p, RelojMonotono { currentTime * 1_000_000L })
        val job = t.iniciar(this)
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        assertTrue(t.estado.value is EstadoDeUbicacion.Disponible)
        job.cancel()
        runCurrent()
    }

    @Test
    fun `E-APP-149 H4 una ultima conocida colgada se abandona pasado su tope y no deja trabajo`() =
        runTest {
            val p = Prov { CompletableDeferred<FixDeUbicacion?>().await() }
            val t =
                TomadorDeUbicacion(
                    p,
                    RelojMonotono { currentTime * 1_000_000L },
                    ventanaMs = 60_000
                )
            t.iniciar(this)
            runCurrent()
            advanceTimeBy(60_001)
            runCurrent()
            assertEquals(0, p.susc)
            assertEquals(EstadoDeUbicacion.SinFix, t.estado.value)
        }
}
