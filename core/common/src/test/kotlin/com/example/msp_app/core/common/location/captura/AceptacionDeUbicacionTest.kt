package com.example.msp_app.core.common.location.captura

import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion.AceptarAntes
import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion.AceptarDespues
import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion.Rechazar
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-140: regla pura de aceptación de la ubicación (antes / después / rechazo).
 *
 * Pruebas de propiedades con semilla fija (kotlin.random): el repo no usa kotest-property y
 * el patrón de [com.example.msp_app.core.common.ventas.precios.ReglaOrdenDePreciosTest] es el mismo.
 */
class AceptacionDeUbicacionTest {

    private val guardado: Instant = Instant.parse("2026-10-30T18:00:00Z")
    private val cfg = ConfigDeAceptacion.POR_DEFECTO

    private fun fix(
        segundos: Double,
        precision: Double = 10.0,
        norteM: Double = 0.0,
        lat: Double? = null,
        lon: Double = BASE_LON,
        velocidad: Double? = null,
        mock: Boolean = false
    ): FixDeUbicacion = fx(
        lat ?: (BASE_LAT + norteM / METROS_POR_GRADO),
        lon,
        precision,
        Math.round(segundos * MS),
        velocidad,
        mock
    )

    /** Fix a [ms] milisegundos del guardado en el reloj monótono; la hora de pared acompaña igual. */
    private fun fx(
        lat: Double,
        lon: Double,
        precision: Double,
        ms: Long,
        velocidad: Double? = null,
        mock: Boolean = false
    ) = FixDeUbicacion(
        lat = lat,
        lon = lon,
        precisionM = precision,
        hora = guardado.plusMillis(ms),
        elapsedRealtimeNanos = GUARDADO_NANOS + ms * 1_000_000L,
        velocidadMs = velocidad,
        esMock = mock
    )

    private fun decidir(vararg fixes: FixDeUbicacion, config: ConfigDeAceptacion = cfg) =
        AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes.toList(), config)

    private fun motivo(d: DecisionDeUbicacion): MotivoDeRechazo? = (d as? Rechazar)?.motivo

    // ---- Reglas básicas -------------------------------------------------------------------

    @Test
    fun `E-APP-140 sin fixes se rechaza SIN_FIX`() {
        assertEquals(Rechazar(MotivoDeRechazo.SIN_FIX), decidir())
    }

    @Test
    fun `E-APP-140 un fix previo bueno se acepta ANTES con hora precision y origen`() {
        val f = fix(-30.0, precision = 12.0)
        val d = decidir(f)
        assertEquals(AceptarAntes(f), d)
        assertEquals(f.hora, d.horaDelFix)
        assertEquals(12.0, d.precisionM!!, 0.0)
        assertEquals(OrigenDeUbicacion.ANTES, d.origen)
    }

    @Test
    fun `E-APP-140 un fix posterior bueno se acepta DESPUES con origen DESPUES`() {
        val f = fix(20.0)
        val d = decidir(f)
        assertEquals(AceptarDespues(f), d)
        assertEquals(OrigenDeUbicacion.DESPUES, d.origen)
    }

    @Test
    fun `E-APP-140 un rechazo no trae hora precision ni origen`() {
        val d = decidir()
        assertNull(d.horaDelFix)
        assertNull(d.precisionM)
        assertNull(d.origen)
    }

    @Test
    fun `E-APP-140 el fix exactamente al instante de guardar cuenta como ANTES`() {
        assertTrue(decidir(fix(0.0)) is AceptarAntes)
    }

    @Test
    fun `E-APP-140 entre varios previos gana el mas cercano al guardado`() {
        val lejano = fix(-25.0, precision = 5.0)
        val cercano = fix(-10.0, precision = 20.0)
        assertEquals(AceptarAntes(cercano), decidir(lejano, cercano))
    }

    @Test
    fun `E-APP-140 el previo mas cercano pero impreciso cede ante uno mas viejo y bueno`() {
        val impreciso = fix(-5.0, precision = 200.0)
        val bueno = fix(-28.0, precision = 20.0)
        assertEquals(AceptarAntes(bueno), decidir(impreciso, bueno))
    }

    @Test
    fun `E-APP-140 a igual hora gana el de mejor precision`() {
        val a = fix(-10.0, precision = 25.0)
        val b = fix(-10.0, precision = 8.0, norteM = 3.0)
        assertEquals(AceptarAntes(b), decidir(a, b))
    }

    @Test
    fun `E-APP-140 ANTES gana aunque haya un posterior mejor`() {
        val antes = fix(-25.0, precision = 25.0)
        val despues = fix(1.0, precision = 3.0)
        assertEquals(AceptarAntes(antes), decidir(antes, despues))
    }

    @Test
    fun `E-APP-140 ANTES no mira movimiento ni velocidad`() {
        val f = fix(-5.0, velocidad = 25.0)
        assertEquals(AceptarAntes(f), decidir(f))
    }

    // ---- Límites exactos ------------------------------------------------------------------

    @Test
    fun `E-APP-140 limite de edad 30 s es INCLUSIVO hacia atras`() {
        assertTrue(decidir(fix(-30.0)) is AceptarAntes)
        assertEquals(MotivoDeRechazo.EDAD_EXCEDIDA, motivo(decidir(fix(-30.001))))
    }

    @Test
    fun `E-APP-140 limite de edad 30 s es INCLUSIVO hacia adelante`() {
        assertTrue(decidir(fix(30.0)) is AceptarDespues)
        assertEquals(MotivoDeRechazo.EDAD_EXCEDIDA, motivo(decidir(fix(30.001))))
    }

    @Test
    fun `E-APP-140 limite de precision 30 m es EXCLUSIVO antes y despues`() {
        assertTrue(decidir(fix(-1.0, precision = 29.99)) is AceptarAntes)
        assertEquals(
            MotivoDeRechazo.PRECISION_INSUFICIENTE,
            motivo(decidir(fix(-1.0, precision = 30.0)))
        )
        assertTrue(decidir(fix(1.0, precision = 29.99)) is AceptarDespues)
        assertEquals(
            MotivoDeRechazo.PRECISION_INSUFICIENTE,
            motivo(decidir(fix(1.0, precision = 30.0)))
        )
    }

    @Test
    fun `E-APP-140 los limites siguen a la config`() {
        val c = ConfigDeAceptacion(maxEdadSeg = 15, maxPrecisionM = 20.0)
        assertTrue(decidir(fix(-15.0, precision = 19.0), config = c) is AceptarAntes)
        assertEquals(MotivoDeRechazo.EDAD_EXCEDIDA, motivo(decidir(fix(-15.001), config = c)))
        assertEquals(
            MotivoDeRechazo.PRECISION_INSUFICIENTE,
            motivo(decidir(fix(-1.0, precision = 20.0), config = c))
        )
    }

    // ---- Datos inválidos ------------------------------------------------------------------

    @Test
    fun `E-APP-140 coordenadas invalidas siempre se rechazan`() {
        val malas = listOf(
            fix(-1.0, lat = 0.0, lon = 0.0),
            fix(-1.0, lat = Double.NaN),
            fix(-1.0, lon = Double.NaN),
            fix(-1.0, lat = Double.POSITIVE_INFINITY),
            fix(-1.0, lon = Double.NEGATIVE_INFINITY),
            fix(-1.0, lat = 90.0001),
            fix(-1.0, lat = -90.0001),
            fix(-1.0, lon = 180.0001),
            fix(-1.0, lon = -180.0001),
            fix(1.0, lat = 0.0, lon = 0.0),
            fix(1.0, lat = Double.NaN)
        )
        malas.forEach {
            assertEquals(
                "$it",
                Rechazar(MotivoDeRechazo.COORDENADAS_INVALIDAS),
                decidir(it)
            )
        }
    }

    @Test
    fun `E-APP-140 solo una de las dos coordenadas en cero es valida`() {
        assertTrue(decidir(fix(-1.0, lat = 0.0, lon = -100.0)) is AceptarAntes)
        assertTrue(decidir(fix(-1.0, lat = 20.0, lon = 0.0)) is AceptarAntes)
    }

    @Test
    fun `E-APP-140 los extremos validos de rango se aceptan`() {
        assertTrue(decidir(fix(-1.0, lat = 90.0, lon = 180.0)) is AceptarAntes)
        assertTrue(decidir(fix(-1.0, lat = -90.0, lon = -180.0)) is AceptarAntes)
    }

    @Test
    fun `E-APP-140 precision cero negativa NaN o infinita se rechaza PRECISION_INVALIDA`() {
        listOf(
            0.0,
            -1.0,
            -0.0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        ).forEach {
            assertEquals(
                "$it",
                Rechazar(MotivoDeRechazo.PRECISION_INVALIDA),
                decidir(fix(-1.0, precision = it))
            )
            assertEquals(
                "$it",
                Rechazar(MotivoDeRechazo.PRECISION_INVALIDA),
                decidir(fix(1.0, precision = it))
            )
        }
    }

    @Test
    fun `E-APP-140 un fix invalido no tapa a uno valido`() {
        val bueno = fix(-20.0)
        assertEquals(
            AceptarAntes(bueno),
            decidir(fix(-1.0, lat = 0.0, lon = 0.0), fix(-2.0, precision = 0.0), bueno)
        )
    }

    @Test
    fun `E-APP-140 el motivo es el del fix mas cercano al guardado`() {
        assertEquals(
            MotivoDeRechazo.PRECISION_INSUFICIENTE,
            motivo(decidir(fix(-2.0, precision = 300.0), fix(-20.0, lat = 0.0, lon = 0.0)))
        )
        assertEquals(
            MotivoDeRechazo.COORDENADAS_INVALIDAS,
            motivo(decidir(fix(-2.0, lat = Double.NaN), fix(-20.0, precision = 300.0)))
        )
    }

    // ---- Movimiento (solo DESPUES) --------------------------------------------------------

    @Test
    fun `E-APP-140 un posterior lejos del previo impreciso se rechaza CON_MOVIMIENTO`() {
        val previo = fix(-30.0, precision = 60.0)
        val tarde = fix(20.0, norteM = 400.0)
        assertEquals(Rechazar(MotivoDeRechazo.CON_MOVIMIENTO), decidir(previo, tarde))
    }

    @Test
    fun `E-APP-140 un posterior cerca del previo impreciso se acepta`() {
        val previo = fix(-30.0, precision = 60.0)
        val tarde = fix(20.0, norteM = 5.0)
        assertEquals(AceptarDespues(tarde), decidir(previo, tarde))
    }

    @Test
    fun `E-APP-140 el umbral de movimiento es el mayor entre precision combinada y el piso`() {
        // precisiones 20 y 25: combinada ~32 m. Con 31 m no hay movimiento; con 33 m si.
        val previo = fix(-300.0, precision = 20.0)
        assertTrue(decidir(previo, fix(20.0, precision = 25.0, norteM = 31.0)) is AceptarDespues)
        assertEquals(
            MotivoDeRechazo.CON_MOVIMIENTO,
            motivo(decidir(previo, fix(20.0, precision = 25.0, norteM = 33.0)))
        )
        // precisiones pequeñas: manda el piso de 15 m.
        val p2 = fix(-300.0, precision = 3.0)
        assertTrue(decidir(p2, fix(20.0, precision = 4.0, norteM = 14.0)) is AceptarDespues)
        assertEquals(
            MotivoDeRechazo.CON_MOVIMIENTO,
            motivo(decidir(p2, fix(20.0, precision = 4.0, norteM = 16.0)))
        )
    }

    @Test
    fun `E-APP-140 la velocidad por encima del maximo es movimiento y el maximo exacto no`() {
        assertTrue(decidir(fix(25.0, velocidad = 1.0)) is AceptarDespues)
        assertEquals(MotivoDeRechazo.CON_MOVIMIENTO, motivo(decidir(fix(25.0, velocidad = 1.01))))
        assertTrue(decidir(fix(25.0, velocidad = null)) is AceptarDespues)
    }

    @Test
    fun `E-APP-140 el movimiento entre fixes posteriores sucesivos rechaza`() {
        val primero = fix(20.0)
        val segundo = fix(25.0, norteM = 300.0)
        assertEquals(Rechazar(MotivoDeRechazo.CON_MOVIMIENTO), decidir(primero, segundo))
    }

    @Test
    fun `E-APP-140 un posterior fuera de ventana no cuenta para el movimiento`() {
        val primero = fix(20.0)
        val fuera = fix(100.0, norteM = 5000.0)
        assertEquals(AceptarDespues(primero), decidir(primero, fuera))
    }

    @Test
    fun `E-APP-140 un posterior impreciso no cuenta para el movimiento`() {
        val primero = fix(20.0)
        val ruido = fix(25.0, precision = 900.0, norteM = 3000.0)
        assertEquals(AceptarDespues(primero), decidir(primero, ruido))
    }

    @Test
    fun `E-APP-140 un posterior sin ningun previo se acepta si no declara velocidad`() {
        val solo = fix(25.0)
        assertEquals(AceptarDespues(solo), decidir(solo))
    }

    @Test
    fun `E-APP-140 el movimiento se mide con haversine y no con grados`() {
        // A 60 grados de latitud un grado de longitud mide ~55 km: 0.0004 grados son ~22 m.
        val a = fx(60.0, 10.0, 5.0, -300_000)
        val b = fx(60.0, 10.0004, 5.0, 10_000)
        val metros = AceptacionDeUbicacion.distanciaM(a, b)
        assertEquals(22.2, metros, 0.5)
        assertEquals(
            MotivoDeRechazo.CON_MOVIMIENTO,
            motivo(AceptacionDeUbicacion.decidir(GUARDADO_NANOS, listOf(a, b)))
        )
    }

    // ---- Config ---------------------------------------------------------------------------

    @Test
    fun `E-APP-140 la config por defecto es 30 s 30 m 15 m 1 mps`() {
        assertEquals(30L, cfg.maxEdadSeg)
        assertEquals(30.0, cfg.maxPrecisionM, 0.0)
        assertEquals(15.0, cfg.minMovimientoM, 0.0)
        assertEquals(1.0, cfg.maxVelocidadMs, 0.0)
    }

    @Test
    fun `E-APP-140 la config rechaza valores fuera de rango o no finitos al construir`() {
        assertThrows(IllegalArgumentException::class.java) { ConfigDeAceptacion(maxEdadSeg = 0) }
        assertThrows(IllegalArgumentException::class.java) { ConfigDeAceptacion(maxEdadSeg = 601) }
        assertThrows(
            IllegalArgumentException::class.java
        ) { ConfigDeAceptacion(maxPrecisionM = 0.5) }
        assertThrows(IllegalArgumentException::class.java) {
            ConfigDeAceptacion(maxPrecisionM = Double.NaN)
        }
        assertThrows(
            IllegalArgumentException::class.java
        ) { ConfigDeAceptacion(maxPrecisionM = 501.0) }
        assertThrows(IllegalArgumentException::class.java) {
            ConfigDeAceptacion(minMovimientoM = Double.POSITIVE_INFINITY)
        }
        assertThrows(
            IllegalArgumentException::class.java
        ) { ConfigDeAceptacion(maxVelocidadMs = 0.0) }
    }

    @Test
    fun `E-APP-140 desdeMapa toma los valores validos`() {
        val c = ConfigDeAceptacion.desdeMapa(
            mapOf(
                ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG to 90,
                ConfigDeAceptacion.CLAVE_MAX_PRECISION_M to 35.5,
                ConfigDeAceptacion.CLAVE_MIN_MOVIMIENTO_M to 20L,
                ConfigDeAceptacion.CLAVE_MAX_VELOCIDAD_MS to 2.0f
            )
        )
        assertEquals(ConfigDeAceptacion(90, 35.5, 20.0, 2.0), c)
    }

    @Test
    fun `E-APP-140 desdeMapa nunca lanza y cae al defecto clave por clave`() {
        val c = ConfigDeAceptacion.desdeMapa(
            mapOf(
                ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG to "30",
                ConfigDeAceptacion.CLAVE_MAX_PRECISION_M to Double.NaN,
                ConfigDeAceptacion.CLAVE_MIN_MOVIMIENTO_M to -3,
                ConfigDeAceptacion.CLAVE_MAX_VELOCIDAD_MS to 999.0
            )
        )
        assertEquals(ConfigDeAceptacion.POR_DEFECTO, c)
        assertEquals(ConfigDeAceptacion.POR_DEFECTO, ConfigDeAceptacion.desdeMapa(emptyMap()))
        assertEquals(
            ConfigDeAceptacion.POR_DEFECTO,
            ConfigDeAceptacion.desdeMapa(
                mapOf(ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG to 1.5, "otra" to null)
            )
        )
        // Tipo exacto: la edad es entera; un decimal (aunque sea 60.0) no se acepta.
        assertEquals(
            ConfigDeAceptacion.POR_DEFECTO,
            ConfigDeAceptacion.desdeMapa(
                mapOf(ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG to 60.0, "x" to 1)
            )
        )
    }

    @Test
    fun `E-APP-141 un valor invalido o fuera de rango conserva el vigente de ese campo`() {
        val vigente =
            ConfigDeAceptacion(
                maxEdadSeg = 45,
                maxPrecisionM = 25.0,
                minMovimientoM = 10.0,
                maxVelocidadMs = 2.0
            )
        val nueva = vigente.actualizadaCon(
            mapOf(
                ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG to 601L,
                ConfigDeAceptacion.CLAVE_MAX_PRECISION_M to "20",
                ConfigDeAceptacion.CLAVE_MIN_MOVIMIENTO_M to Double.NaN,
                ConfigDeAceptacion.CLAVE_MAX_VELOCIDAD_MS to true
            )
        )
        assertEquals(vigente, nueva)
    }

    @Test
    fun `E-APP-141 los limites del rango seguro son inclusivos y lo de afuera se rechaza`() {
        fun con(clave: String, v: Any) =
            ConfigDeAceptacion.POR_DEFECTO.actualizadaCon(mapOf(clave to v))
        assertEquals(1L, con(ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG, 1L).maxEdadSeg)
        assertEquals(600L, con(ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG, 600L).maxEdadSeg)
        assertEquals(30L, con(ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG, 0L).maxEdadSeg)
        assertEquals(30L, con(ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG, -5L).maxEdadSeg)
        assertEquals(1.0, con(ConfigDeAceptacion.CLAVE_MAX_PRECISION_M, 1.0).maxPrecisionM, 0.0)
        assertEquals(500.0, con(ConfigDeAceptacion.CLAVE_MAX_PRECISION_M, 500.0).maxPrecisionM, 0.0)
        assertEquals(30.0, con(ConfigDeAceptacion.CLAVE_MAX_PRECISION_M, 500.1).maxPrecisionM, 0.0)
        assertEquals(30.0, con(ConfigDeAceptacion.CLAVE_MAX_PRECISION_M, 0.99).maxPrecisionM, 0.0)
        assertEquals(
            30.0,
            con(ConfigDeAceptacion.CLAVE_MAX_PRECISION_M, Double.POSITIVE_INFINITY).maxPrecisionM,
            0.0
        )
    }

    @Test
    fun `E-APP-141 quitar una clave del documento devuelve ese campo al defecto`() {
        val vigente = ConfigDeAceptacion(maxEdadSeg = 45, maxPrecisionM = 25.0)
        assertEquals(ConfigDeAceptacion.POR_DEFECTO, vigente.actualizadaCon(emptyMap()))
        assertEquals(
            ConfigDeAceptacion(maxEdadSeg = 45),
            vigente.actualizadaCon(
                mapOf(
                    ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG to 45L,
                    ConfigDeAceptacion.CLAVE_MAX_PRECISION_M to null
                )
            )
        )
    }

    @Test
    fun `E-APP-141 propiedad actualizadaCon nunca lanza y siempre produce una config dentro de rango`() {
        val rnd = Random(SEMILLA + 11)
        val claves = listOf(
            ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG,
            ConfigDeAceptacion.CLAVE_MAX_PRECISION_M,
            ConfigDeAceptacion.CLAVE_MIN_MOVIMIENTO_M,
            ConfigDeAceptacion.CLAVE_MAX_VELOCIDAD_MS
        )
        repeat(ITERACIONES) {
            val mapa = claves.associateWith<String, Any?> {
                when (rnd.nextInt(8)) {
                    0 -> null
                    1 -> "x"
                    2 -> Double.NaN
                    3 -> rnd.nextLong(-1000, 1000)
                    4 -> rnd.nextDouble(-1000.0, 1000.0)
                    5 -> rnd.nextInt(-5, 700)
                    6 -> Double.NEGATIVE_INFINITY
                    else -> rnd.nextBoolean()
                }
            }
            val c = ConfigDeAceptacion.POR_DEFECTO.actualizadaCon(mapa)
            assertTrue(c.maxEdadSeg in ConfigDeAceptacion.RANGO_EDAD)
            assertTrue(c.maxPrecisionM in ConfigDeAceptacion.RANGO_PRECISION)
            assertEquals(c, c.actualizadaCon(mapa))
        }
    }

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
        val falsoLejos = fix(10.0, norteM = 5000.0, mock = true)
        assertEquals(AceptarDespues(tarde), decidir(falsoLejos, tarde))
        val previoFalso = fix(-40.0, precision = 60.0, norteM = 5000.0, mock = true)
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

    private companion object {
        const val BASE_LAT = 20.5888
        const val BASE_LON = -100.3899
        const val METROS_POR_GRADO = 111_194.9
        const val MS = 1000.0
        const val SEMILLA = 140L
        const val GUARDADO_NANOS = 5_000_000_000_000L
        const val ITERACIONES = 2000
    }
}
