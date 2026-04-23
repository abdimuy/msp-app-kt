package com.example.msp_app.features.dailyReport.domain

import com.example.msp_app.features.transfers.data.api.dto.TransferListItemDto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the transfer side of the daily-report timezone bug.
 *
 * Before the fix:
 *  1. The repository asked the backend for exactly one day (`fechaInicio = fechaFin = today`).
 *     If the backend interpreted those as UTC-day bounds (the likely case when timestamps
 *     are stored UTC), transfers made after 18:00 CDMX were missing.
 *  2. The displayed hour used `OffsetDateTime.parse(...).format(...)` which renders in the
 *     original offset — so `2026-04-16T00:05:00Z` rendered as `12:05 AM` instead of the
 *     real business-zone time `6:05 PM`.
 *
 * The fix widens the server window and applies [onBusinessDate] client-side so the report
 * is correct regardless of server timezone semantics. Tests below prove the filter is
 * tight around that.
 */
class TransferDateFilterTest {

    private fun transfer(id: Int, fechaHoraCreacion: String?): TransferListItemDto =
        TransferListItemDto(
            doctoInId = id,
            almacenId = 1,
            almacenDestinoId = 2,
            fecha = "2026-04-15",
            folio = "T-$id",
            fechaHoraCreacion = fechaHoraCreacion
        )

    @Test
    fun `includes late-evening transfer in the business date it belongs to`() {
        // 18:05 CDMX 15-abr == 00:05 UTC 16-abr.
        val late = transfer(1, "2026-04-16T00:05:00Z")
        val filtered = listOf(late).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertEquals(listOf(late), filtered)
    }

    @Test
    fun `excludes that same transfer from the next day's report`() {
        val late = transfer(1, "2026-04-16T00:05:00Z")
        val filtered = listOf(late).onBusinessDate(LocalDate.of(2026, 4, 16))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `accepts offset-suffixed timestamps`() {
        // Backend might send "2026-04-15T18:05:00-06:00" instead of UTC; equivalent Instant.
        val t = transfer(1, "2026-04-15T18:05:00-06:00")
        val filtered = listOf(t).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertEquals(1, filtered.size)
    }

    @Test
    fun `null fechaHoraCreacion is excluded silently`() {
        val t = transfer(1, null)
        val filtered = listOf(t).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `malformed fechaHoraCreacion is excluded silently`() {
        val t = transfer(1, "garbage")
        val filtered = listOf(t).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `mixed batch from widened server window narrows to one business day`() {
        // Simulates a response covering yesterday..tomorrow.
        val yesterday = transfer(1, "2026-04-14T20:00:00Z") // 14:00 CDMX 14-abr
        val todayMorning = transfer(2, "2026-04-15T14:00:00Z") // 08:00 CDMX 15-abr
        val todayEvening = transfer(3, "2026-04-16T00:05:00Z") // 18:05 CDMX 15-abr
        val tomorrow = transfer(4, "2026-04-16T14:00:00Z") // 08:00 CDMX 16-abr

        val filtered = listOf(yesterday, todayMorning, todayEvening, tomorrow)
            .onBusinessDate(LocalDate.of(2026, 4, 15))

        assertEquals(listOf("T-2", "T-3"), filtered.map { it.folio })
    }

    /**
     * Fence test: the scenario that reproduces the production symptom ("transfers from
     * other days appear; today's late transfers are missing"). If this test breaks,
     * someone regressed the filter.
     */
    @Test
    fun `bug regression fence`() {
        val productionBug = transfer(1, "2026-04-16T00:05:00Z")
        assertTrue(
            "precondition: ISO must have the UTC-next-day prefix that tripped the old filter",
            !productionBug.fechaHoraCreacion!!.startsWith("2026-04-15")
        )
        val filtered = listOf(productionBug).onBusinessDate(LocalDate.of(2026, 4, 15))
        assertEquals(listOf(productionBug), filtered)
    }
}
