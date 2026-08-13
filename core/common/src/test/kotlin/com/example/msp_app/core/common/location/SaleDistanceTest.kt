package com.example.msp_app.core.common.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleDistanceTest {

    @Test
    fun `of con metros validos devuelve Known`() {
        assertEquals(SaleDistance.Known(0.0), SaleDistance.of(0.0))
        assertEquals(SaleDistance.Known(1.0), SaleDistance.of(1.0))
        assertEquals(SaleDistance.Known(1234.5), SaleDistance.of(1234.5))
    }

    @Test
    fun `of con el centinela heredado devuelve Unknown`() {
        // El defecto original: Long.MAX_VALUE significaba "sin ubicacion" y
        // viajaba como si fuera una distancia.
        assertEquals(SaleDistance.Unknown, SaleDistance.of(Long.MAX_VALUE))
        assertEquals(SaleDistance.Unknown, SaleDistance.of(Long.MAX_VALUE.toDouble()))
    }

    @Test
    fun `of con valores no finitos devuelve Unknown`() {
        assertEquals(SaleDistance.Unknown, SaleDistance.of(Double.NaN))
        assertEquals(SaleDistance.Unknown, SaleDistance.of(Double.POSITIVE_INFINITY))
        assertEquals(SaleDistance.Unknown, SaleDistance.of(Double.NEGATIVE_INFINITY))
        assertEquals(SaleDistance.Unknown, SaleDistance.of(Double.MAX_VALUE))
    }

    @Test
    fun `of con distancia negativa devuelve Unknown`() {
        assertEquals(SaleDistance.Unknown, SaleDistance.of(-1.0))
        assertEquals(SaleDistance.Unknown, SaleDistance.of(-0.5))
        assertEquals(SaleDistance.Unknown, SaleDistance.of(Long.MIN_VALUE))
    }

    @Test
    fun `of respeta el limite de la circunferencia terrestre`() {
        assertEquals(
            SaleDistance.Known(SaleDistance.MAX_PLAUSIBLE_METERS),
            SaleDistance.of(SaleDistance.MAX_PLAUSIBLE_METERS)
        )
        assertEquals(
            SaleDistance.Unknown,
            SaleDistance.of(SaleDistance.MAX_PLAUSIBLE_METERS + 1.0)
        )
    }

    @Test
    fun `Known rechaza el centinela en el constructor`() {
        assertThrows(IllegalArgumentException::class.java) {
            SaleDistance.Known(Long.MAX_VALUE.toDouble())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaleDistance.Known(Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaleDistance.Known(-1.0)
        }
    }

    @Test
    fun `Known rechaza el centinela tambien via copy`() {
        val valida = SaleDistance.Known(100.0)
        assertThrows(IllegalArgumentException::class.java) {
            valida.copy(meters = Long.MAX_VALUE.toDouble())
        }
    }

    @Test
    fun `metersOrNull expone los metros solo cuando hay ubicacion`() {
        assertEquals(42.0, SaleDistance.of(42.0).metersOrNull)
        assertNull(SaleDistance.Unknown.metersOrNull)
    }

    @Test
    fun `ordena de la mas cercana a la mas lejana`() {
        val ordenadas = listOf(
            SaleDistance.of(900.0),
            SaleDistance.of(10.0),
            SaleDistance.of(0.0),
            SaleDistance.of(150.0)
        ).sorted()

        assertEquals(
            listOf(
                SaleDistance.of(0.0),
                SaleDistance.of(10.0),
                SaleDistance.of(150.0),
                SaleDistance.of(900.0)
            ),
            ordenadas
        )
    }

    @Test
    fun `las ventas sin ubicacion quedan al final`() {
        val ordenadas = listOf(
            SaleDistance.Unknown,
            SaleDistance.of(900.0),
            SaleDistance.Unknown,
            SaleDistance.of(10.0)
        ).sorted()

        assertEquals(
            listOf(
                SaleDistance.of(10.0),
                SaleDistance.of(900.0),
                SaleDistance.Unknown,
                SaleDistance.Unknown
            ),
            ordenadas
        )
    }

    @Test
    fun `sin ubicacion ordena despues incluso de la distancia maxima`() {
        val maxima = SaleDistance.of(SaleDistance.MAX_PLAUSIBLE_METERS)
        assertTrue(maxima < SaleDistance.Unknown)
        assertTrue(SaleDistance.Unknown > maxima)
    }

    @Test
    fun `dos sin ubicacion son equivalentes al ordenar`() {
        assertEquals(0, SaleDistance.Unknown.compareTo(SaleDistance.Unknown))
    }

    @Test
    fun `una lista solo de sin ubicacion conserva su tamano al ordenar`() {
        val soloDesconocidas = List(3) { SaleDistance.Unknown }
        assertEquals(soloDesconocidas, soloDesconocidas.sorted())
    }
}
