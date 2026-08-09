package com.example.msp_app.feature.collectionreport.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Helpers de etiqueta de [DateRange]: días incluidos, día final, y la etiqueta
 * de ciclo en sus tres formas (un día, mismo mes, y cruce de mes/año). Bordes
 * en zona negocio; formato es-MX determinista.
 */
class DateRangeTest {

    @Test
    fun `days cuenta los dias incluidos con fin exclusivo`() {
        val range = DateRange("2026-08-03T06:00:00Z", "2026-08-08T06:00:00Z")
        assertEquals(5, range.days)
        assertEquals(LocalDate.of(2026, 8, 3), range.startDate)
        assertEquals(LocalDate.of(2026, 8, 7), range.endInclusiveDate)
        assertEquals(LocalDate.of(2026, 8, 8), range.endExclusiveDate)
    }

    @Test
    fun `dayLabel formatea el dia final incluido`() {
        val range = DateRange("2026-08-07T06:00:00Z", "2026-08-08T06:00:00Z")
        assertEquals("viernes 7 ago 2026", range.dayLabel())
    }

    @Test
    fun `cycleLabel de un solo dia omite el rango`() {
        val range = DateRange("2026-08-07T06:00:00Z", "2026-08-08T06:00:00Z")
        assertEquals("semana · vie 7 ago · 1 día", range.cycleLabel())
    }

    @Test
    fun `cycleLabel mismo mes omite el mes en el inicio`() {
        val range = DateRange("2026-08-03T06:00:00Z", "2026-08-08T06:00:00Z")
        assertEquals("semana · lun 3 – vie 7 ago · 5 días", range.cycleLabel())
    }

    @Test
    fun `cycleLabel cruce de mes lleva mes en ambos extremos`() {
        val range = DateRange("2026-07-30T06:00:00Z", "2026-08-08T06:00:00Z")
        assertEquals(9, range.days)
        assertEquals("semana · jue 30 jul – vie 7 ago · 9 días", range.cycleLabel())
        assertEquals("viernes 7 ago 2026", range.dayLabel())
    }

    @Test
    fun `cycleLabel cruce de año lleva mes en ambos extremos`() {
        val range = DateRange("2025-12-30T06:00:00Z", "2026-01-03T06:00:00Z")
        assertEquals(4, range.days)
        assertEquals("semana · mar 30 dic – vie 2 ene · 4 días", range.cycleLabel())
        assertEquals("viernes 2 ene 2026", range.dayLabel())
    }
}
