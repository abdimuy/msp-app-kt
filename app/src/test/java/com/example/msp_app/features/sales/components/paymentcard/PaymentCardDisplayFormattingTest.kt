package com.example.msp_app.features.sales.components.paymentcard

import com.example.msp_app.core.common.time.AppTime
import java.time.DayOfWeek
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD suite for Task 9 of the fechas/AppTime migration — the payment-date label in
 * `PaymentCard.kt`, migrated off `DateUtils.formatIsoDate` to `AppTime.formatIsoForDisplay`
 * with pattern `"EE dd/MM/yyyy hh:mm a"` (weekday abbreviation + date + 12h time). The
 * composable uppercases the result afterwards — that `.uppercase()` step is UI-only and out
 * of scope here, matching Task 8's approach of testing the `AppTime` call, not the Compose
 * layer.
 *
 * `PaymentCard.kt` lines 48-58 (`DateUtils.getIsoDateTime` / `DateUtils.isAfterIso`, used to
 * pick the card's background gradient) are OUT OF SCOPE for this task: they are comparison
 * logic, not a `formatIsoDate`/`formatLocalDateTime` display call — left untouched per the
 * Task 9 brief's explicit scope.
 */
class PaymentCardDisplayFormattingTest {

    @Test
    fun `EE dd-MM-yyyy hh-mm a formats a known Z instant in CDMX`() {
        // 2026-04-15 18:30 UTC == 12:30 PM CDMX (a Wednesday in business zone)
        val result = AppTime.formatIsoForDisplay("2026-04-15T18:30:00Z", "EE dd/MM/yyyy hh:mm a")
        assertTrue("got: $result", result.contains("15/04/2026 12:30"))
    }

    @Test
    fun `EE dd-MM-yyyy hh-mm a shows the correct business day for 23-00 CDMX (next-day UTC)`() {
        // 23:00 CDMX 2026-04-15 (Wednesday) == 05:00 UTC 2026-04-16 (would read Thursday if
        // formatted in the device's UTC zone instead of business zone).
        val result = AppTime.formatIsoForDisplay("2026-04-16T05:00:00Z", "EE dd/MM/yyyy hh:mm a")
        assertTrue("got: $result", result.contains("15/04/2026 11:00"))
    }

    @Test
    fun `EE dd-MM-yyyy hh-mm a returns the raw string for malformed input, no crash`() {
        assertEquals(
            "sin-fecha",
            AppTime.formatIsoForDisplay("sin-fecha", "EE dd/MM/yyyy hh:mm a")
        )
    }

    @Test
    fun `EE dd-MM-yyyy hh-mm a is independent of the device default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val iso = "2026-04-16T05:00:00Z"

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = AppTime.formatIsoForDisplay(iso, "EE dd/MM/yyyy hh:mm a")

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = AppTime.formatIsoForDisplay(iso, "EE dd/MM/yyyy hh:mm a")

            assertEquals(underUtc, underTijuana)
            assertTrue("got: $underUtc", underUtc.contains("15/04/2026 11:00"))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `fixture instant is a Wednesday in business zone`() {
        val d = AppTime.toBusinessDate(Instant.parse("2026-04-15T18:30:00Z"))
        assertEquals(DayOfWeek.WEDNESDAY, d.dayOfWeek)
    }
}
