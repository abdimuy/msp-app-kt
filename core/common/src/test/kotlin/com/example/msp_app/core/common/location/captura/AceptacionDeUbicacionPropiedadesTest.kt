package com.example.msp_app.core.common.location.captura

import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion.AceptarAntes
import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion.AceptarDespues
import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion.Rechazar
import java.time.Duration
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-140: propiedades (semilla fija) y casos de relojes de [AceptacionDeUbicacion]. */
class AceptacionDeUbicacionPropiedadesTest : AceptacionDeUbicacionBase() {

    // ---- Propiedades ----------------------------------------------------------------------

    private fun aleatorio(rnd: Random): FixDeUbicacion {
        val tipo = rnd.nextInt(20)
        val lat = when (tipo) {
            0 -> Double.NaN
            1 -> 0.0
            2 -> 95.0
            else -> BASE_LAT + rnd.nextDouble(-0.003, 0.003)
        }
        val lon = when (tipo) {
            1 -> 0.0
            3 -> -190.0
            else -> BASE_LON + rnd.nextDouble(-0.003, 0.003)
        }
        val precision = when (tipo) {
            4 -> 0.0
            5 -> -5.0
            6 -> Double.NaN
            else -> rnd.nextDouble(0.5, 60.0)
        }
        val velocidad = if (rnd.nextInt(4) == 0) rnd.nextDouble(0.0, 4.0) else null
        return fx(
            lat,
            lon,
            precision,
            rnd.nextLong(-60_000, 60_000),
            velocidad,
            rnd.nextInt(25) == 0
        )
    }

    private fun escenario(rnd: Random): List<FixDeUbicacion> = List(rnd.nextInt(0, 7)) {
        aleatorio(rnd)
    }

    private fun valido(f: FixDeUbicacion) = f.lat.isFinite() && f.lon.isFinite() &&
        f.lat in -90.0..90.0 && f.lon in -180.0..180.0 && !(f.lat == 0.0 && f.lon == 0.0) &&
        f.precisionM.isFinite() && f.precisionM > 0.0 && !f.esMock

    @Test
    fun `E-APP-140 propiedad total y un fix aceptado siempre cumple la regla`() {
        val rnd = Random(SEMILLA)
        var aceptadas = 0
        repeat(ITERACIONES) {
            val fixes = escenario(rnd)
            val d = AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes)
            if (d is DecisionDeUbicacion.Aceptada) {
                aceptadas++
                val f = d.fix
                assertTrue("el fix aceptado debe venir de la entrada", f in fixes)
                assertTrue("coordenadas y precision validas: $f", valido(f))
                assertTrue("precision < 30: $f", f.precisionM < cfg.maxPrecisionM)
                assertTrue(
                    "edad <= 30 s: $f",
                    kotlin.math.abs(f.elapsedRealtimeNanos - GUARDADO_NANOS) <= cfg.maxEdadSeg * 1_000_000_000L
                )
                assertEquals(f.elapsedRealtimeNanos > GUARDADO_NANOS, d is AceptarDespues)
            } else {
                assertTrue(d is Rechazar)
            }
        }
        // Control positivo: el generador produce escenarios aceptados y rechazados.
        assertTrue("aceptadas=$aceptadas", aceptadas in 100 until ITERACIONES - 100)
    }

    @Test
    fun `E-APP-140 propiedad determinismo y el orden de la lista no importa`() {
        val rnd = Random(SEMILLA + 1)
        repeat(ITERACIONES) {
            val fixes = escenario(rnd)
            val d = AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes)
            assertEquals(d, AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes))
            assertEquals(d, AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes.shuffled(rnd)))
            assertEquals(d, AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes.reversed()))
        }
    }

    @Test
    fun `E-APP-140 propiedad monotonia en edad con un solo fix y mas edad nunca vuelve aceptado un rechazo`() {
        val rnd = Random(SEMILLA + 2)
        repeat(ITERACIONES) {
            val lado = if (rnd.nextBoolean()) 1 else -1
            val precision = rnd.nextDouble(0.5, 60.0)
            val vel = if (rnd.nextBoolean()) rnd.nextDouble(0.0, 3.0) else null
            val a = rnd.nextLong(0, 60_000)
            val b = rnd.nextLong(0, 60_000)
            val (cerca, lejos) = if (a <= b) a to b else b to a
            fun decide(ms: Long) = AceptacionDeUbicacion.decidir(
                GUARDADO_NANOS,
                listOf(fx(BASE_LAT, BASE_LON, precision, lado * ms, vel))
            )
            if (decide(lejos) is DecisionDeUbicacion.Aceptada) {
                assertTrue(
                    "cerca=$cerca lejos=$lejos",
                    decide(cerca) is DecisionDeUbicacion.Aceptada
                )
            }
        }
    }

    @Test
    fun `E-APP-140 propiedad monotonia en precision con un solo fix`() {
        val rnd = Random(SEMILLA + 3)
        repeat(ITERACIONES) {
            val ms = rnd.nextLong(-40_000, 40_000)
            val a = rnd.nextDouble(0.1, 60.0)
            val b = rnd.nextDouble(0.1, 60.0)
            fun decide(p: Double) = AceptacionDeUbicacion.decidir(
                GUARDADO_NANOS,
                listOf(fx(BASE_LAT, BASE_LON, p, ms))
            )
            if (decide(maxOf(a, b)) is DecisionDeUbicacion.Aceptada) {
                assertTrue(decide(minOf(a, b)) is DecisionDeUbicacion.Aceptada)
            }
        }
    }

    @Test
    fun `E-APP-140 propiedad simetria antes y despues para un fix sin velocidad`() {
        val rnd = Random(SEMILLA + 4)
        repeat(ITERACIONES) {
            val ms = rnd.nextLong(1, 50_000)
            val p = rnd.nextDouble(-5.0, 50.0)
            val lat = if (rnd.nextInt(10) == 0) Double.NaN else BASE_LAT
            fun decide(signo: Int) = AceptacionDeUbicacion.decidir(
                GUARDADO_NANOS,
                listOf(fx(lat, BASE_LON, p, signo * ms))
            )
            val antes = decide(-1)
            val despues = decide(1)
            assertEquals(
                "ms=$ms p=$p",
                antes is DecisionDeUbicacion.Aceptada,
                despues is DecisionDeUbicacion.Aceptada
            )
            assertEquals(motivo(antes), motivo(despues))
        }
    }

    @Test
    fun `E-APP-140 propiedad idempotencia la decision sobre su propio fix es la misma`() {
        val rnd = Random(SEMILLA + 5)
        repeat(ITERACIONES) {
            val d = AceptacionDeUbicacion.decidir(GUARDADO_NANOS, escenario(rnd))
            if (d is DecisionDeUbicacion.Aceptada) {
                assertEquals(d, AceptacionDeUbicacion.decidir(GUARDADO_NANOS, listOf(d.fix)))
                assertEquals(d, AceptacionDeUbicacion.decidir(GUARDADO_NANOS, listOf(d.fix, d.fix)))
            }
        }
    }

    @Test
    fun `E-APP-140 propiedad agregar fixes posteriores a un ANTES no cambia la decision`() {
        val rnd = Random(SEMILLA + 6)
        var casos = 0
        repeat(ITERACIONES) {
            val fixes = escenario(rnd)
            val d = AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes)
            if (d is AceptarAntes) {
                casos++
                val extra = List(
                    3
                ) { aleatorio(rnd) }.filter { it.elapsedRealtimeNanos > GUARDADO_NANOS }
                assertEquals(d, AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes + extra))
            }
        }
        assertTrue("casos=$casos", casos > 50)
    }

    @Test
    fun `E-APP-140 propiedad agregar fixes nunca convierte un rechazo por movimiento en aceptacion`() {
        val rnd = Random(SEMILLA + 7)
        var casos = 0
        repeat(ITERACIONES * 2) {
            val fixes = escenario(rnd)
            if (motivo(AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes)) == MotivoDeRechazo.CON_MOVIMIENTO) {
                casos++
                // Agregar fixes posteriores a la ventana o a la referencia no puede quitar el movimiento:
                // solo se agregan fixes que no son anteriores al guardado (no cambian la referencia).
                val extra = List(
                    3
                ) { aleatorio(rnd) }.filter { it.elapsedRealtimeNanos > GUARDADO_NANOS }
                val d = AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes + extra)
                assertTrue(
                    "$fixes + $extra -> $d",
                    d is Rechazar && d.motivo == MotivoDeRechazo.CON_MOVIMIENTO
                )
            }
        }
        assertTrue("casos=$casos", casos > 20)
    }

    @Test
    fun `E-APP-140 propiedad agregar fixes posteriores a un DESPUES solo lo deja igual o lo rechaza`() {
        val rnd = Random(SEMILLA + 8)
        var casos = 0
        repeat(ITERACIONES * 2) {
            val fixes = escenario(rnd)
            val d = AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes)
            if (d is AceptarDespues) {
                casos++
                val extra = List(3) {
                    aleatorio(rnd)
                }.filter { it.elapsedRealtimeNanos > d.fix.elapsedRealtimeNanos }
                val d2 = AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes + extra)
                assertTrue(
                    "$d -> $d2",
                    d2 == d || (d2 is Rechazar && d2.motivo == MotivoDeRechazo.CON_MOVIMIENTO)
                )
            }
        }
        assertTrue("casos=$casos", casos > 20)
    }

    @Test
    fun `E-APP-140 propiedad la hora de pared no influye solo el reloj monotono`() {
        val rnd = Random(SEMILLA + 9)
        repeat(ITERACIONES) {
            val fixes = escenario(rnd)
            val delta = Duration.ofSeconds(rnd.nextLong(-1_000_000, 1_000_000))
            val saltados = fixes.map { it.copy(hora = it.hora.plus(delta)) }
            assertEquals(
                motivoOAceptada(AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes)),
                motivoOAceptada(AceptacionDeUbicacion.decidir(GUARDADO_NANOS, saltados))
            )
        }
    }

    @Test
    fun `E-APP-140 un salto del reloj de pared no hace viejo ni nuevo a un fix`() {
        // Misma medicion monotona (hace 10 s), pero la hora de pared del fix dice que fue ayer.
        val f = fx(
            BASE_LAT,
            BASE_LON,
            10.0,
            -10_000
        ).let { it.copy(hora = it.hora.minusSeconds(86_400)) }
        assertEquals(AceptarAntes(f), decidir(f))
        // Y al reves: hora de pared al dia, pero el monotono dice que tiene 10 minutos.
        val viejo = fx(BASE_LAT, BASE_LON, 10.0, -600_000).let { it.copy(hora = guardado) }
        assertEquals(Rechazar(MotivoDeRechazo.EDAD_EXCEDIDA), decidir(viejo))
    }

    @Test
    fun `E-APP-140 una ubicacion simulada se rechaza antes y despues`() {
        assertEquals(Rechazar(MotivoDeRechazo.UBICACION_SIMULADA), decidir(fix(-5.0, mock = true)))
        assertEquals(Rechazar(MotivoDeRechazo.UBICACION_SIMULADA), decidir(fix(5.0, mock = true)))
    }

    @Test
    fun `E-APP-140 un fix simulado no tapa a uno real ni cuenta como movimiento`() {
        val real = fix(-20.0)
        assertEquals(AceptarAntes(real), decidir(fix(-1.0, mock = true), real))
        val tarde = fix(25.0)
        val falsoLejos = fix(10.0, lat = norte(5000.0), mock = true)
        assertEquals(AceptarDespues(tarde), decidir(falsoLejos, tarde))
        val previoFalso = fix(-40.0, precision = 60.0, lat = norte(5000.0), mock = true)
        assertEquals(AceptarDespues(tarde), decidir(previoFalso, tarde))
    }

    @Test
    fun `E-APP-140 los relojes en el limite de Long no desbordan`() {
        val f = fix(-1.0).copy(elapsedRealtimeNanos = Long.MIN_VALUE)
        assertEquals(
            Rechazar(MotivoDeRechazo.EDAD_EXCEDIDA),
            AceptacionDeUbicacion.decidir(Long.MAX_VALUE, listOf(f))
        )
        val g = fix(1.0).copy(elapsedRealtimeNanos = Long.MAX_VALUE)
        assertEquals(
            Rechazar(MotivoDeRechazo.EDAD_EXCEDIDA),
            AceptacionDeUbicacion.decidir(Long.MIN_VALUE, listOf(g))
        )
    }

    private fun motivoOAceptada(d: DecisionDeUbicacion): Any = when (d) {
        is DecisionDeUbicacion.Aceptada -> d.origen!! to d.fix.precisionM
        is Rechazar -> d.motivo
    }
}
