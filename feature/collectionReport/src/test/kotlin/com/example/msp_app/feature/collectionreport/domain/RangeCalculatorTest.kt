package com.example.msp_app.feature.collectionreport.domain

import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Robustez SUPREMA de los rangos half-open en zona negocio: fin EXCLUSIVO,
 * ciclo del cobrador `[FECHA_CARGA_INICIAL, hoy]`, fallback null, cruce de
 * medianoche/mes/año y una transición DST histórica de México (2021). JVM puro
 * con [FakeClock] — determinista, sin zona del dispositivo.
 */
class RangeCalculatorTest {

    // 2026-08-07 12:00 en America/Mexico_City (offset -06:00, sin DST desde 2022).
    private val noonAug7Cdmx = FakeClock(Instant.parse("2026-08-07T18:00:00Z"))

    private fun contains(range: DateRange, iso: String): Boolean {
        val t = Instant.parse(iso)
        val start = Instant.parse(range.startIso)
        val end = Instant.parse(range.endExclusiveIso)
        return !t.isBefore(start) && t.isBefore(end)
    }

    // region — dayRange

    @Test
    fun `dayRange serializa medianoche negocio a wire UTC`() {
        val range = RangeCalculator.dayRange(noonAug7Cdmx)
        assertEquals("2026-08-07T06:00:00Z", range.startIso)
        assertEquals("2026-08-08T06:00:00Z", range.endExclusiveIso)
    }

    @Test
    fun `dayRange char-test un pago a las 23-59-59 SI cae dentro de hoy`() {
        val range = RangeCalculator.dayRange(noonAug7Cdmx)
        // 23:59:59 del 7-ago en CDMX == 05:59:59Z del 8-ago.
        assertTrue(contains(range, "2026-08-08T05:59:59Z"))
    }

    @Test
    fun `dayRange el fin es EXCLUSIVO - 00-00-00 del dia siguiente NO cae dentro`() {
        val range = RangeCalculator.dayRange(noonAug7Cdmx)
        // 00:00:00 del 8-ago en CDMX == 06:00:00Z, igual al borde exclusivo.
        assertFalse(contains(range, "2026-08-08T06:00:00Z"))
    }

    @Test
    fun `dayRange incluye el inicio 00-00-00`() {
        val range = RangeCalculator.dayRange(noonAug7Cdmx)
        assertTrue(contains(range, "2026-08-07T06:00:00Z"))
    }

    // region — cycleRange

    @Test
    fun `cycleRange abarca desde la carga inicial hasta el fin exclusivo de hoy`() {
        // Carga: 3-ago 10:00 CDMX.
        val range = RangeCalculator.cycleRange(noonAug7Cdmx, Instant.parse("2026-08-03T16:00:00Z"))
        assertEquals("2026-08-03T06:00:00Z", range.startIso)
        assertEquals("2026-08-08T06:00:00Z", range.endExclusiveIso)
        assertEquals(5, range.days)
    }

    @Test
    fun `cycleRange fin EXCLUSIVO - pago de hoy a las 23-59-59 SI cuenta`() {
        val range = RangeCalculator.cycleRange(noonAug7Cdmx, Instant.parse("2026-08-03T16:00:00Z"))
        assertTrue(contains(range, "2026-08-08T05:59:59Z"))
        assertFalse(contains(range, "2026-08-08T06:00:00Z"))
    }

    @Test
    fun `cycleRange con carga null cae a dayRange`() {
        assertEquals(
            RangeCalculator.dayRange(noonAug7Cdmx),
            RangeCalculator.cycleRange(noonAug7Cdmx, null)
        )
    }

    @Test
    fun `cycleRange con carga hoy es un ciclo de un dia`() {
        val range = RangeCalculator.cycleRange(noonAug7Cdmx, Instant.parse("2026-08-07T15:00:00Z"))
        assertEquals("2026-08-07T06:00:00Z", range.startIso)
        assertEquals("2026-08-08T06:00:00Z", range.endExclusiveIso)
        assertEquals(1, range.days)
    }

    @Test
    fun `cycleRange usa la fecha de negocio de la carga, no la del sistema`() {
        // Carga a las 23:30 CDMX del 2-ago (== 05:30Z del 3-ago). La fecha de
        // negocio es 2-ago, no 3-ago: el rango debe iniciar el 2-ago.
        val range = RangeCalculator.cycleRange(noonAug7Cdmx, Instant.parse("2026-08-03T05:30:00Z"))
        assertEquals("2026-08-02T06:00:00Z", range.startIso)
    }

    // region — cycleInfo (etiquetas)

    @Test
    fun `cycleInfo produce dias y etiquetas es-MX`() {
        val info = RangeCalculator.cycleInfo(noonAug7Cdmx, Instant.parse("2026-08-03T16:00:00Z"))
        assertEquals(5, info.days)
        assertEquals("semana · lun 3 – vie 7 ago · 5 días", info.cycleLabel)
        assertEquals("viernes 7 ago 2026", info.dayLabel)
    }

    @Test
    fun `cycleInfo ciclo de un dia usa singular dia`() {
        val info = RangeCalculator.cycleInfo(noonAug7Cdmx, Instant.parse("2026-08-07T15:00:00Z"))
        assertEquals(1, info.days)
        assertEquals("semana · vie 7 ago · 1 día", info.cycleLabel)
        assertEquals("viernes 7 ago 2026", info.dayLabel)
    }

    @Test
    fun `cycleInfo con carga null equivale a un dia`() {
        assertEquals(1, RangeCalculator.cycleInfo(noonAug7Cdmx, null).days)
    }

    // region — DST histórica (México observó DST hasta 2022)

    @Test
    fun `cycleRange cruza la transicion DST de otono 2021 sin perder dias`() {
        // Hoy: 1-nov-2021 12:00 CDMX (ya en horario estándar -06:00).
        val clock = FakeClock(Instant.parse("2021-11-01T18:00:00Z"))
        // Carga: 30-oct-2021 (aún en DST -05:00).
        val range = RangeCalculator.cycleRange(clock, Instant.parse("2021-10-30T15:00:00Z"))
        assertEquals("2021-10-30T05:00:00Z", range.startIso)
        assertEquals("2021-11-02T06:00:00Z", range.endExclusiveIso)
        assertEquals(3, range.days)
    }
}
