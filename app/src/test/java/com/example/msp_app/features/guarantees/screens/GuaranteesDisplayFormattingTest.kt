package com.example.msp_app.features.guarantees.screens

import com.example.msp_app.core.common.time.AppTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD suite for Task 9 of the fechas/AppTime migration — display-only call sites migrated off
 * the legacy date util's `formatIsoDate` in the guarantees flow. Pins `AppTime.formatIsoForDisplay`
 * behaviour for each exact pattern carried over from the legacy date util, following the same approach
 * as Task 8's `PaymentDisplayFormattingTest`.
 *
 * Patterns under test:
 *  - `"dd/MM/yyyy"` — `GuaranteesScreen.kt` (FECHA_SOLICITUD, NOTIFICADO state)
 *  - `"dd MMM yyyy, hh:mm a"` — `GuaranteeDetailScreen.kt` (FECHA_SOLICITUD in the list row)
 *  - `"dd MMM yyyy"` — `GuaranteeListItem.kt` (FECHA_SOLICITUD)
 *
 * All three call sites previously passed `locale = Locale("es", "MX")` explicitly; that is
 * now [AppTime.formatIsoForDisplay]'s default ([AppTime.BUSINESS_LOCALE]) and is omitted.
 */
class GuaranteesDisplayFormattingTest {

    // region — "dd/MM/yyyy" (GuaranteesScreen)

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
    fun `dd-MM-yyyy returns the raw string for malformed input, no crash`() {
        assertEquals("dato-corrupto", AppTime.formatIsoForDisplay("dato-corrupto", "dd/MM/yyyy"))
    }

    // endregion

    // region — "dd MMM yyyy, hh:mm a" (GuaranteeDetailScreen)

    @Test
    fun `dd MMM yyyy hh-mm a formats a known Z instant in CDMX with the Spanish month abbreviation`() {
        val result = AppTime.formatIsoForDisplay("2026-04-15T18:30:00Z", "dd MMM yyyy, hh:mm a")
        assertTrue("got: $result", result.startsWith("15 abr 2026, 12:30"))
    }

    @Test
    fun `dd MMM yyyy hh-mm a shows the correct business day for 23-00 CDMX (next-day UTC)`() {
        val result = AppTime.formatIsoForDisplay("2026-04-16T05:00:00Z", "dd MMM yyyy, hh:mm a")
        assertTrue("got: $result", result.startsWith("15 abr 2026, 11:00"))
    }

    @Test
    fun `dd MMM yyyy hh-mm a returns the raw string for malformed input, no crash`() {
        assertEquals(
            "garantia-sin-fecha",
            AppTime.formatIsoForDisplay("garantia-sin-fecha", "dd MMM yyyy, hh:mm a")
        )
    }

    // endregion

    // region — "dd MMM yyyy" (GuaranteeListItem)

    @Test
    fun `dd MMM yyyy formats a known Z instant in CDMX with the Spanish month abbreviation`() {
        val result = AppTime.formatIsoForDisplay("2026-04-15T18:30:00Z", "dd MMM yyyy")
        assertEquals("15 abr 2026", result)
    }

    @Test
    fun `dd MMM yyyy shows the correct business day for 23-00 CDMX (next-day UTC)`() {
        val result = AppTime.formatIsoForDisplay("2026-04-16T05:00:00Z", "dd MMM yyyy")
        assertEquals("15 abr 2026", result)
    }

    @Test
    fun `dd MMM yyyy is independent of the device default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val iso = "2026-04-16T05:00:00Z"

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = AppTime.formatIsoForDisplay(iso, "dd MMM yyyy")

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = AppTime.formatIsoForDisplay(iso, "dd MMM yyyy")

            assertEquals(underUtc, underTijuana)
            assertEquals("15 abr 2026", underUtc)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // endregion
}
