package com.example.msp_app.features.dailyReport.domain

import com.example.msp_app.data.local.entities.LocalSaleEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the daily-report timezone bug.
 *
 * The bug: before the fix the repository filtered `FECHA_VENTA.startsWith("yyyy-MM-dd")`
 * where the prefix came from `LocalDate.now()` (local zone) but `FECHA_VENTA` is stored
 * as UTC ISO-8601. For sales made after 18:00 CDMX the UTC date was already the next
 * day, so late-evening sales either disappeared from today's report or bled into
 * tomorrow's.
 *
 * These tests anchor the fix by:
 *   1. Building sales with realistic UTC-stored `FECHA_VENTA` values.
 *   2. Asking "which belong to business date X?" and asserting the right subset.
 */
class SaleDateFilterTest {

    private fun sale(id: String, fechaVenta: String): LocalSaleEntity = LocalSaleEntity(
        LOCAL_SALE_ID = id,
        NOMBRE_CLIENTE = "Cliente $id",
        FECHA_VENTA = fechaVenta,
        LATITUD = 0.0,
        LONGITUD = 0.0,
        DIRECCION = "",
        PARCIALIDAD = 0.0,
        ENGANCHE = null,
        TELEFONO = "",
        FREC_PAGO = "Semanal",
        AVAL_O_RESPONSABLE = null,
        NOTA = null,
        DIA_COBRANZA = "Lunes",
        PRECIO_TOTAL = 0.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        MONTO_DE_CONTADO = 0.0,
        ENVIADO = false
    )

    @Test
    fun `includes late-evening sale in the business date it belongs to`() {
        // Venta a las 18:05 CDMX del 15-abr (UTC ya es 16-abr 00:05).
        val late = sale(id = "late", fechaVenta = "2026-04-16T00:05:00Z")
        val filtered = listOf(late).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertEquals(listOf(late), filtered)
    }

    @Test
    fun `excludes that same sale from the next day's report`() {
        val late = sale(id = "late", fechaVenta = "2026-04-16T00:05:00Z")
        val filtered = listOf(late).onBusinessDate(LocalDate.of(2026, 4, 16))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `mid-morning sale behaves normally`() {
        val midMorning = sale(id = "mid", fechaVenta = "2026-04-15T15:30:00Z") // 09:30 CDMX
        val filtered = listOf(midMorning).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertEquals(1, filtered.size)
    }

    @Test
    fun `boundary - sale at exactly midnight CDMX lands on that new day`() {
        // 00:00 CDMX == 06:00 UTC; should belong to the UTC date's day in CDMX.
        val midnight = sale(id = "mid", fechaVenta = "2026-04-15T06:00:00Z")
        val filtered = listOf(midnight).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertEquals(1, filtered.size)
    }

    @Test
    fun `boundary - sale one second before midnight CDMX stays on prior day`() {
        // 23:59:59 CDMX 2026-04-14 == 05:59:59 UTC 2026-04-15.
        val endOfPrior = sale(id = "end", fechaVenta = "2026-04-15T05:59:59Z")
        val filteredPrior = listOf(endOfPrior).onBusinessDate(LocalDate.of(2026, 4, 14))
        val filteredNext = listOf(endOfPrior).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertEquals(1, filteredPrior.size)
        assertTrue(filteredNext.isEmpty())
    }

    @Test
    fun `malformed FECHA_VENTA is excluded silently not crashed`() {
        val bad = sale(id = "bad", fechaVenta = "not-a-date")
        val filtered = listOf(bad).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `blank FECHA_VENTA is excluded silently`() {
        val empty = sale(id = "empty", fechaVenta = "")
        val filtered = listOf(empty).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `mixed batch - keeps only sales matching the target date`() {
        val yesterday = sale("y", "2026-04-14T18:00:00Z") // 12:00 CDMX 14-abr
        val todayMorning = sale("tm", "2026-04-15T15:00:00Z") // 09:00 CDMX 15-abr
        val todayLate = sale("tl", "2026-04-16T00:05:00Z") // 18:05 CDMX 15-abr
        val tomorrow = sale("t", "2026-04-16T15:00:00Z") // 09:00 CDMX 16-abr

        val filtered = listOf(yesterday, todayMorning, todayLate, tomorrow)
            .onBusinessDate(LocalDate.of(2026, 4, 15))

        assertEquals(
            "should contain exactly the two sales made on business date 15-abr",
            listOf("tm", "tl"),
            filtered.map { it.LOCAL_SALE_ID }
        )
    }

    @Test
    fun `empty input returns empty`() {
        assertTrue(emptyList<LocalSaleEntity>().onBusinessDate(LocalDate.of(2026, 4, 15)).isEmpty())
    }

    /**
     * Fence test: this scenario is the exact shape of the original production bug.
     * A sale created at 18:05 CDMX on 15-abr is stored with a UTC-prefixed FECHA_VENTA
     * of 2026-04-16. The old `startsWith("2026-04-15")` filter would miss it; the new
     * filter must include it. If this ever starts failing, someone reverted the fix.
     */
    @Test
    fun `bug regression fence`() {
        val productionBug = sale(id = "late-evening", fechaVenta = "2026-04-16T00:05:00Z")
        val startsWithOldPrefix = productionBug.FECHA_VENTA.startsWith("2026-04-15")
        assertTrue(
            "precondition: FECHA_VENTA must have the UTC-next-day prefix that tripped the old filter",
            !startsWithOldPrefix
        )
        val filtered = listOf(productionBug).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertEquals(
            "fix must include this sale in the 15-abr business-date report",
            listOf(productionBug),
            filtered
        )
    }
}
