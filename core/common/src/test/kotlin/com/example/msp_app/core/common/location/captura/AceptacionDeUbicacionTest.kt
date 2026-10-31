package com.example.msp_app.core.common.location.captura

import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion.AceptarAntes
import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion.AceptarDespues
import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion.Rechazar
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
class AceptacionDeUbicacionTest : AceptacionDeUbicacionBase() {

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
        val b = fix(-10.0, precision = 8.0, lat = norte(3.0))
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
        val tarde = fix(20.0, lat = norte(400.0))
        assertEquals(Rechazar(MotivoDeRechazo.CON_MOVIMIENTO), decidir(previo, tarde))
    }

    @Test
    fun `E-APP-140 un posterior cerca del previo impreciso se acepta`() {
        val previo = fix(-30.0, precision = 60.0)
        val tarde = fix(20.0, lat = norte(5.0))
        assertEquals(AceptarDespues(tarde), decidir(previo, tarde))
    }

    @Test
    fun `E-APP-140 el umbral de movimiento es el mayor entre precision combinada y el piso`() {
        // precisiones 20 y 25: combinada ~32 m. Con 31 m no hay movimiento; con 33 m si.
        val previo = fix(-300.0, precision = 20.0)
        assertTrue(
            decidir(previo, fix(20.0, precision = 25.0, lat = norte(31.0))) is AceptarDespues
        )
        assertEquals(
            MotivoDeRechazo.CON_MOVIMIENTO,
            motivo(decidir(previo, fix(20.0, precision = 25.0, lat = norte(33.0))))
        )
        // precisiones pequeñas: manda el piso de 15 m.
        val p2 = fix(-300.0, precision = 3.0)
        assertTrue(decidir(p2, fix(20.0, precision = 4.0, lat = norte(14.0))) is AceptarDespues)
        assertEquals(
            MotivoDeRechazo.CON_MOVIMIENTO,
            motivo(decidir(p2, fix(20.0, precision = 4.0, lat = norte(16.0))))
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
        val segundo = fix(25.0, lat = norte(300.0))
        assertEquals(Rechazar(MotivoDeRechazo.CON_MOVIMIENTO), decidir(primero, segundo))
    }

    @Test
    fun `E-APP-140 un posterior fuera de ventana no cuenta para el movimiento`() {
        val primero = fix(20.0)
        val fuera = fix(100.0, lat = norte(5000.0))
        assertEquals(AceptarDespues(primero), decidir(primero, fuera))
    }

    @Test
    fun `E-APP-140 un posterior impreciso no cuenta para el movimiento`() {
        val primero = fix(20.0)
        val ruido = fix(25.0, precision = 900.0, lat = norte(3000.0))
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
                ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG to 45,
                ConfigDeAceptacion.CLAVE_MAX_PRECISION_M to 35.5,
                ConfigDeAceptacion.CLAVE_MIN_MOVIMIENTO_M to 20L,
                ConfigDeAceptacion.CLAVE_MAX_VELOCIDAD_MS to 2.0f
            )
        )
        assertEquals(ConfigDeAceptacion(45, 35.5, 20.0, 2.0), c)
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
                ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG to 61L,
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
        assertEquals(60L, con(ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG, 60L).maxEdadSeg)
        assertEquals(30L, con(ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG, 61L).maxEdadSeg)
        assertEquals(30L, con(ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG, 0L).maxEdadSeg)
        assertEquals(30L, con(ConfigDeAceptacion.CLAVE_MAX_EDAD_SEG, -5L).maxEdadSeg)
        assertEquals(1.0, con(ConfigDeAceptacion.CLAVE_MAX_PRECISION_M, 1.0).maxPrecisionM, 0.0)
        assertEquals(50.0, con(ConfigDeAceptacion.CLAVE_MAX_PRECISION_M, 50.0).maxPrecisionM, 0.0)
        assertEquals(30.0, con(ConfigDeAceptacion.CLAVE_MAX_PRECISION_M, 50.1).maxPrecisionM, 0.0)
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
}
