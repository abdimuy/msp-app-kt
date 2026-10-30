package com.example.msp_app.core.common.location.captura

import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-146 (K5): la regla que decide a que captura guardada sin ubicacion se le escribe un fix
 * tardio. Pura y determinista: el instante es un parametro, no un reloj.
 */
class CapturasEnEsperaTest {

    private fun fix(
        msDesdeT0: Long,
        lat: Double = 20.5888,
        lon: Double = -100.3899,
        precision: Double = 10.0,
        mock: Boolean = false
    ) = FixDeUbicacion(
        lat = lat,
        lon = lon,
        precisionM = precision,
        hora = Instant.parse("2026-10-30T18:00:00Z").plusMillis(msDesdeT0),
        elapsedRealtimeNanos = BASE + msDesdeT0 * NANOS_POR_MS,
        esMock = mock
    )

    private fun nanos(msDesdeT0: Long) = BASE + msDesdeT0 * NANOS_POR_MS

    private fun espera() = CapturasEnEspera().also { it.registrar("a", "pago", nanos(0)) }

    @Test
    fun `E-APP-146 un fix valido posterior al guardado ubica la captura y la saca de la lista`() {
        val e = espera()
        val f = fix(5_000)

        val ubicadas = e.resolver(nanos(5_000), listOf(f))

        assertEquals(1, ubicadas.size)
        assertEquals("a", ubicadas.single().captura.id)
        assertEquals(DecisionDeUbicacion.AceptarDespues(f), ubicadas.single().decision)
        assertEquals(
            "una captura se ubica una sola vez",
            emptyList<CapturaEnEspera>(),
            e.pendientes()
        )
        assertEquals(emptyList<CapturaUbicada>(), e.resolver(nanos(6_000), listOf(f, fix(6_000))))
    }

    @Test
    fun `E-APP-146 sin fix valido la captura sigue esperando`() {
        val e = espera()
        assertEquals(emptyList<CapturaUbicada>(), e.resolver(nanos(2_000), emptyList()))
        assertEquals(
            "imprecisa",
            emptyList<CapturaUbicada>(),
            e.resolver(nanos(2_000), listOf(fix(2_000, precision = 30.0)))
        )
        assertEquals(
            "simulada",
            emptyList<CapturaUbicada>(),
            e.resolver(nanos(2_000), listOf(fix(2_000, mock = true)))
        )
        assertEquals(1, e.pendientes().size)
    }

    @Test
    fun `E-APP-146 un fix fuera de la ventana no ubica y la captura se descarta`() {
        val e = espera()
        // 31 s despues del guardado: la captura expiro (ventana de 30 s).
        assertEquals(emptyList<CapturaUbicada>(), e.resolver(nanos(31_000), listOf(fix(31_000))))
        assertEquals(emptyList<CapturaEnEspera>(), e.pendientes())
    }

    @Test
    fun `E-APP-146 el limite de la ventana es inclusivo`() {
        val e = espera()
        assertEquals(1, e.resolver(nanos(30_000), listOf(fix(30_000))).size)
    }

    @Test
    fun `E-APP-146 con movimiento entre fixes no se ubica y se descarta`() {
        val e = espera()
        val a = fix(4_000)
        val b = fix(9_000, lat = 20.5888 + 0.001) // ~111 m
        assertEquals(emptyList<CapturaUbicada>(), e.resolver(nanos(9_000), listOf(a, b)))
        assertEquals(emptyList<CapturaEnEspera>(), e.pendientes())
    }

    @Test
    fun `E-APP-146 cada captura se evalua contra su propio instante de guardado`() {
        val e = CapturasEnEspera()
        e.registrar("vieja", "pago", nanos(0))
        e.registrar("nueva", "pago", nanos(20_000))
        // Un fix a +25 s: para "vieja" esta a 25 s (cabe); para "nueva" esta a 5 s (cabe).
        val ubicadas = e.resolver(nanos(25_000), listOf(fix(25_000)))
        assertEquals(listOf("vieja", "nueva"), ubicadas.map { it.captura.id })
    }

    @Test
    fun `E-APP-146 un fix mas nuevo que el instante de resolver no cuenta`() {
        val e = espera()
        assertEquals(emptyList<CapturaUbicada>(), e.resolver(nanos(3_000), listOf(fix(8_000))))
        assertEquals(1, e.pendientes().size)
    }

    @Test
    fun `E-APP-146 registrar el mismo id otra vez no cambia su instante de guardado`() {
        val e = CapturasEnEspera()
        e.registrar("a", "pago", nanos(0))
        e.registrar("a", "pago", nanos(20_000))
        assertEquals(nanos(0), e.pendientes().single().guardadoNanos)
    }

    @Test
    fun `E-APP-146 la capacidad acotada suelta primero la mas vieja`() {
        val e = CapturasEnEspera(capacidad = 2)
        e.registrar("a", "pago", nanos(0))
        e.registrar("b", "pago", nanos(1))
        e.registrar("c", "pago", nanos(2))
        assertEquals(listOf("b", "c"), e.pendientes().map { it.id })
    }

    @Test
    fun `E-APP-146 olvidar quita la captura`() {
        val e = espera()
        e.olvidar("a")
        assertEquals(emptyList<CapturaEnEspera>(), e.pendientes())
    }

    @Test
    fun `E-APP-146 propiedad - lo ubicado siempre es valido y nada se ubica dos veces`() {
        val azar = Random(146)
        var ubicadasEnTotal = 0
        var descartadasSinUbicar = 0
        repeat(ITERACIONES) {
            val e = CapturasEnEspera()
            val ids = (0 until azar.nextInt(1, 5)).map { "c$it" }
            ids.forEachIndexed { i, id -> e.registrar(id, "pago", nanos(i * 3_000L)) }
            val fixes = (0 until azar.nextInt(0, 6)).map {
                fix(
                    azar.nextLong(0, 45_000),
                    lat = 20.5888 + azar.nextDouble(-0.0003, 0.0003),
                    precision = azar.nextDouble(1.0, 60.0),
                    mock = azar.nextInt(10) == 0
                )
            }
            val vistos = mutableSetOf<String>()
            val ahora = nanos(azar.nextLong(0, 50_000))
            e.resolver(ahora, fixes).forEach { u ->
                assertTrue("una captura no se ubica dos veces", vistos.add(u.captura.id))
                val f = u.decision.fix
                assertTrue("sin mock", !f.esMock)
                assertTrue("precision < 30", f.precisionM < 30.0)
                assertTrue("no es del futuro", f.elapsedRealtimeNanos <= ahora)
                assertTrue(
                    "dentro de 30 s del guardado",
                    kotlin.math.abs(f.elapsedRealtimeNanos - u.captura.guardadoNanos) <=
                        30_000L * NANOS_POR_MS
                )
            }
            ubicadasEnTotal += vistos.size
            descartadasSinUbicar += ids.size - vistos.size - e.pendientes().size
            assertEquals(
                emptyList<CapturaUbicada>(),
                e.resolver(ahora, fixes).filter {
                    it.captura.id in vistos
                }
            )
        }
        // Control positivo del generador: produce capturas ubicadas Y descartadas sin ubicar.
        assertTrue("el generador ubica algunas ($ubicadasEnTotal)", ubicadasEnTotal > 100)
        assertTrue("y descarta otras ($descartadasSinUbicar)", descartadasSinUbicar > 100)
    }

    private companion object {
        const val BASE = 5_000_000_000_000L
        const val NANOS_POR_MS = 1_000_000L
        const val ITERACIONES = 2_000
    }
}
