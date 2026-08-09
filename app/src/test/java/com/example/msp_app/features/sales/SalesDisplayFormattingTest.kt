package com.example.msp_app.features.sales

import com.example.msp_app.core.common.time.AppTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD suite for Task 9 of the fechas/AppTime migration — display-only call sites migrated off
 * the legacy date util's `formatIsoDate` in the sales flow.
 *
 * As in Task 8's `PaymentDisplayFormattingTest`, this pins `AppTime.formatIsoForDisplay`
 * behaviour for each exact pattern string carried over from the legacy date util, rather than
 * re-testing `AppTime` generically (already covered by
 * `core/common/.../time/AppTimeTest.kt`).
 *
 * Patterns under test:
 *  - `"dd/MM/yyyy HH:mm a"` — `SalesListScreen.kt` / `UnifiedSalesScreen.kt` (sale card date)
 *  - `"dd/MM/yyyy"` (== [AppTime.Formats.DATE_SHORT]) — `SaleDetailsViewModel.kt` (sale FECHA)
 */
class SalesDisplayFormattingTest {

    // region — "dd/MM/yyyy HH:mm a" (SalesListScreen / UnifiedSalesScreen sale card)

    @Test
    fun `dd-MM-yyyy HH-mm a formats a known Z instant in CDMX`() {
        // 22:30 UTC == 16:30 CDMX (UTC-6, no DST since Mexico dropped it in 2022)
        val result = AppTime.formatIsoForDisplay("2026-04-15T22:30:00Z", "dd/MM/yyyy HH:mm a")
        assertTrue("got: $result", result.startsWith("15/04/2026 16:30"))
    }

    @Test
    fun `dd-MM-yyyy HH-mm a shows the correct business day for 23-00 CDMX (next-day UTC)`() {
        // 23:00 CDMX 2026-04-15 == 05:00 UTC 2026-04-16
        val result = AppTime.formatIsoForDisplay("2026-04-16T05:00:00Z", "dd/MM/yyyy HH:mm a")
        assertTrue("got: $result", result.startsWith("15/04/2026 23:00"))
    }

    @Test
    fun `dd-MM-yyyy HH-mm a returns the raw string for malformed input, no crash`() {
        assertEquals(
            "fecha-invalida",
            AppTime.formatIsoForDisplay("fecha-invalida", "dd/MM/yyyy HH:mm a")
        )
    }

    @Test
    fun `dd-MM-yyyy HH-mm a is independent of the device default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val iso = "2026-04-16T05:00:00Z"

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = AppTime.formatIsoForDisplay(iso, "dd/MM/yyyy HH:mm a")

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = AppTime.formatIsoForDisplay(iso, "dd/MM/yyyy HH:mm a")

            assertEquals(underUtc, underTijuana)
            assertTrue("got: $underUtc", underUtc.startsWith("15/04/2026 23:00"))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // endregion

    // region — "dd/MM/yyyy" (SaleDetailsViewModel.FECHA, == AppTime.Formats.DATE_SHORT)

    @Test
    fun `dd-MM-yyyy formats a known Z instant in CDMX`() {
        val result = AppTime.formatIsoForDisplay("2026-04-15T18:30:00Z", AppTime.Formats.DATE_SHORT)
        assertEquals("15/04/2026", result)
    }

    @Test
    fun `dd-MM-yyyy shows the correct business day for 23-00 CDMX (next-day UTC)`() {
        val result = AppTime.formatIsoForDisplay("2026-04-16T05:00:00Z", AppTime.Formats.DATE_SHORT)
        assertEquals("15/04/2026", result)
    }

    @Test
    fun `dd-MM-yyyy returns the raw string for malformed input, no crash`() {
        assertEquals("N-A", AppTime.formatIsoForDisplay("N-A", AppTime.Formats.DATE_SHORT))
    }

    // endregion
}
