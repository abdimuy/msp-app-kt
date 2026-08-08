package com.example.msp_app.features.payments

import com.example.msp_app.core.common.time.AppTime
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD suite for Task 8 of the fechas/AppTime migration — display-only call sites of
 * `AppTime.formatIsoForDisplay` in the payments flow (`PaymentItem`, `PaymentTicketScreen`,
 * `Home.kt`), migrated off `DateUtils.formatIsoDate`.
 *
 * These call sites pass the pattern string as their only non-default argument, so this suite
 * pins `formatIsoForDisplay` behaviour for each exact pattern in use rather than re-testing
 * `AppTime` generically (already covered in `core/common/.../time/AppTimeTest.kt`).
 *
 * Patterns under test:
 *  - `"dd/MM/yyyy hh:mm a"`   — `PaymentItem.kt` (82, 165)
 *  - `"dd/MM/yyyy"`           — `PaymentTicketScreen.kt` (137, buildPaymentLines)
 *  - `"EEEE, dd/MM/yyyy"`     — `PaymentTicketScreen.kt` (148, buildPaymentLineData)
 *  - `"dd/MM/yy HH:mm"`       — `PaymentTicketScreen.kt` (165, ticket header date)
 *  - `"EEE. dd/MM/yyyy hh:mm a"` — `Home.kt` (273, inicio de semana)
 */
class PaymentDisplayFormattingTest {

    // region — "dd/MM/yyyy hh:mm a" (PaymentItem)

    @Test
    fun `dd-MM-yyyy hh-mm a formats a known Z instant in CDMX es-MX`() {
        // 18:30 UTC == 12:30 PM CDMX
        val result = AppTime.formatIsoForDisplay("2026-04-15T18:30:00Z", "dd/MM/yyyy hh:mm a")
        assertTrue("got: $result", result.startsWith("15/04/2026 12:30"))
    }

    @Test
    fun `dd-MM-yyyy hh-mm a shows the correct business day for 23-00 CDMX (next-day UTC)`() {
        // 23:00 CDMX 2026-04-15 == 05:00 UTC 2026-04-16
        val result = AppTime.formatIsoForDisplay("2026-04-16T05:00:00Z", "dd/MM/yyyy hh:mm a")
        assertTrue("got: $result", result.startsWith("15/04/2026 11:00"))
    }

    @Test
    fun `dd-MM-yyyy hh-mm a returns the raw string for malformed input`() {
        assertEquals(
            "no-es-una-fecha",
            AppTime.formatIsoForDisplay("no-es-una-fecha", "dd/MM/yyyy hh:mm a")
        )
    }

    @Test
    fun `dd-MM-yyyy hh-mm a is independent of the device default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val iso = "2026-04-16T05:00:00Z"

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = AppTime.formatIsoForDisplay(iso, "dd/MM/yyyy hh:mm a")

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = AppTime.formatIsoForDisplay(iso, "dd/MM/yyyy hh:mm a")

            assertEquals(underUtc, underTijuana)
            assertTrue("got: $underUtc", underUtc.startsWith("15/04/2026 11:00"))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // endregion

    // region — "dd/MM/yyyy" (PaymentTicketScreen.buildPaymentLines)

    @Test
    fun `dd-MM-yyyy formats a known Z instant in CDMX`() {
        val result = AppTime.formatIsoForDisplay("2026-04-15T18:30:00Z", "dd/MM/yyyy")
        assertEquals("15/04/2026", result)
    }

    @Test
    fun `dd-MM-yyyy shows the correct business day for 23-00 CDMX (next-day UTC)`() {
        val result = AppTime.formatIsoForDisplay("2026-04-16T05:00:00Z", "dd/MM/yyyy")
        assertEquals("15/04/2026", result)
    }

    @Test
    fun `dd-MM-yyyy returns the raw string for malformed input`() {
        assertEquals("garbage", AppTime.formatIsoForDisplay("garbage", "dd/MM/yyyy"))
    }

    // endregion

    // region — "EEEE, dd/MM/yyyy" (PaymentTicketScreen.buildPaymentLineData)

    @Test
    fun `EEEE dd-MM-yyyy formats the Spanish weekday name in CDMX`() {
        // 2026-04-15 is a Wednesday.
        val result = AppTime.formatIsoForDisplay("2026-04-15T18:30:00Z", "EEEE, dd/MM/yyyy")
        assertEquals("miércoles, 15/04/2026", result)
    }

    @Test
    fun `EEEE dd-MM-yyyy shows the correct business day for 23-00 CDMX (next-day UTC)`() {
        // 23:00 CDMX 2026-04-15 (Wednesday) == 05:00 UTC 2026-04-16 (would be Thursday if
        // formatted in the device's UTC zone instead of business zone).
        val result = AppTime.formatIsoForDisplay("2026-04-16T05:00:00Z", "EEEE, dd/MM/yyyy")
        assertEquals("miércoles, 15/04/2026", result)
    }

    @Test
    fun `EEEE dd-MM-yyyy returns the raw string for malformed input`() {
        assertEquals("xyz", AppTime.formatIsoForDisplay("xyz", "EEEE, dd/MM/yyyy"))
    }

    // endregion

    // region — "dd/MM/yy HH:mm" (PaymentTicketScreen ticket header date)

    @Test
    fun `dd-MM-yy HH-mm formats a known Z instant in CDMX 24h`() {
        val result = AppTime.formatIsoForDisplay("2026-04-15T18:30:00Z", "dd/MM/yy HH:mm")
        assertEquals("15/04/26 12:30", result)
    }

    @Test
    fun `dd-MM-yy HH-mm shows the correct business day for 23-00 CDMX (next-day UTC)`() {
        val result = AppTime.formatIsoForDisplay("2026-04-16T05:00:00Z", "dd/MM/yy HH:mm")
        assertEquals("15/04/26 23:00", result)
    }

    @Test
    fun `dd-MM-yy HH-mm returns empty string for a null payment (selectedPayment not loaded yet)`() {
        // PaymentTicketScreen calls this with `selectedPayment?.FECHA_HORA_PAGO`, which is null
        // before the payment loads — must render as empty, never crash or show "null".
        assertEquals("", AppTime.formatIsoForDisplay(null, "dd/MM/yy HH:mm"))
    }

    // endregion

    // region — "EEE. dd/MM/yyyy hh:mm a" (Home.kt inicio de semana)

    @Test
    fun `EEE dd-MM-yyyy hh-mm a formats a known Z instant in CDMX es-MX`() {
        val result = AppTime.formatIsoForDisplay("2026-04-15T18:30:00Z", "EEE. dd/MM/yyyy hh:mm a")
        assertTrue("got: $result", result.startsWith("mié. 15/04/2026 12:30"))
    }

    @Test
    fun `EEE dd-MM-yyyy hh-mm a shows the correct business day for 23-00 CDMX (next-day UTC)`() {
        val result = AppTime.formatIsoForDisplay("2026-04-16T05:00:00Z", "EEE. dd/MM/yyyy hh:mm a")
        assertTrue("got: $result", result.startsWith("mié. 15/04/2026 11:00"))
    }

    @Test
    fun `EEE dd-MM-yyyy hh-mm a returns the literal 'null' string when FECHA_CARGA_INICIAL is absent`() {
        // Home.kt builds the iso string as
        // `userData?.FECHA_CARGA_INICIAL?.toDate()?.toInstant()?.atZone(ZoneOffset.UTC).toString()`
        // — when the chain is null, Kotlin's Any?.toString() yields the literal string "null"
        // (four characters), not an actual null reference. formatIsoForDisplay must fail to
        // parse it and fall back to the raw "null" string, matching the pre-migration behaviour.
        val result = AppTime.formatIsoForDisplay("null", "EEE. dd/MM/yyyy hh:mm a")
        assertEquals("null", result)
    }

    @Test
    fun `EEE dd-MM-yyyy hh-mm a is independent of the device default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val iso = "2026-04-16T05:00:00Z"

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = AppTime.formatIsoForDisplay(iso, "EEE. dd/MM/yyyy hh:mm a")

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = AppTime.formatIsoForDisplay(iso, "EEE. dd/MM/yyyy hh:mm a")

            assertEquals(underUtc, underTijuana)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // endregion

    // Sanity check that the fixed instant used across this suite is really a Wednesday in
    // business zone, so the weekday-name assertions above are not accidentally testing the
    // wrong day.
    @Test
    fun `fixture instant is a Wednesday in business zone`() {
        val d = AppTime.toBusinessDate(Instant.parse("2026-04-15T18:30:00Z"))
        assertEquals(java.time.DayOfWeek.WEDNESDAY, d.dayOfWeek)
    }
}
