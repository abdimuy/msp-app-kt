package com.example.msp_app.features.home.components.homestartweeksection

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.features.sales.viewmodels.currentSalesLastSync
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 12 (fechas/AppTime migration) — reproduces and eliminates bug #8: the "última
 * sincronización" round-trip between [com.example.msp_app.features.sales.viewmodels.SalesViewModel.syncSales]
 * (write, via [currentSalesLastSync]) and `HomeStartWeekSection` (read, via
 * [formatLastSyncForDisplay]) used to format/parse through independent
 * `SimpleDateFormat(..., Locale.getDefault())` calls on each side — a double round-trip
 * through the device's mutable default locale.
 */
class LastSyncDisplayTest {

    // region — 1. Round trip, fixed clock, default locale

    @Test
    fun `write then read round-trip reproduces the direct display format for the same instant`() {
        val fixed = Instant.parse("2026-08-08T14:30:00Z") // 08:30 CDMX (no DST since 2022)
        val clock = FakeClock(fixed)

        val persisted = currentSalesLastSync(clock)
        val displayed = formatLastSyncForDisplay(persisted)

        assertEquals(AppTime.formatForDisplay(fixed, AppTime.Formats.DATE_TIME_12H), displayed)
        // Digits are locale-invariant regardless of the AM/PM marker's exact spelling
        // ("AM" vs "a. m." across JDKs).
        assertTrue("got: $displayed", displayed.startsWith("08/08/2026 08:30"))
    }

    @Test
    fun `blank persisted value shows the not-yet-synced message, not a parse failure string`() {
        assertEquals("No se ha sincronizado aún", formatLastSyncForDisplay(""))
        assertEquals("No se ha sincronizado aún", formatLastSyncForDisplay("   "))
    }

    // endregion

    // region — 2. Bug #8: independent of Locale.getDefault() at read time, and of the locale
    // that was active when the value was WRITTEN — the two are no longer coupled.

    @Test
    fun `round trip is independent of Locale-getDefault at write time`() {
        val original = Locale.getDefault()
        try {
            val fixed = Instant.parse("2026-08-08T14:30:00Z")
            val clock = FakeClock(fixed)

            Locale.setDefault(Locale.US)
            val persistedUnderUs = currentSalesLastSync(clock)

            Locale.setDefault(Locale("ar"))
            val persistedUnderAr = currentSalesLastSync(clock)

            // The wire format written under two very different default locales must be
            // byte-identical — persistence never touches Locale.getDefault().
            assertEquals(persistedUnderUs, persistedUnderAr)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `round trip is independent of Locale-getDefault at read time`() {
        val original = Locale.getDefault()
        try {
            val fixed = Instant.parse("2026-08-08T14:30:00Z")
            val persisted = currentSalesLastSync(FakeClock(fixed))

            Locale.setDefault(Locale.US)
            val displayedUnderUs = formatLastSyncForDisplay(persisted)

            Locale.setDefault(Locale("ar"))
            val displayedUnderAr = formatLastSyncForDisplay(persisted)

            Locale.setDefault(Locale("es", "MX"))
            val displayedUnderEsMx = formatLastSyncForDisplay(persisted)

            // formatIsoForDisplay always renders with the fixed BUSINESS_LOCALE, never
            // Locale.getDefault(), so all three must be identical.
            assertEquals(displayedUnderUs, displayedUnderAr)
            assertEquals(displayedUnderUs, displayedUnderEsMx)
            assertTrue(displayedUnderUs.startsWith("08/08/2026 08:30"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `locale changing between write and read no longer breaks the round trip`() {
        val original = Locale.getDefault()
        try {
            val fixed = Instant.parse("2026-08-08T14:30:00Z")

            // Write under one locale...
            Locale.setDefault(Locale.US)
            val persisted = currentSalesLastSync(FakeClock(fixed))

            // ...device locale changes (e.g. user changes system language)...
            Locale.setDefault(Locale("ar"))

            // ...read must still succeed and produce the correct CDMX wall-clock time.
            val displayed = formatLastSyncForDisplay(persisted)

            assertTrue("got: $displayed", displayed.startsWith("08/08/2026 08:30"))
        } finally {
            Locale.setDefault(original)
        }
    }

    // endregion

    // region — 3. Mutation-kill: reproduces the OLD bug shape to prove this test would have
    // caught it — the old write format used a space separator and no zone/offset, which is
    // NOT one of the shapes AppTime.parseWireFormat accepts, so it degrades to the raw string
    // instead of a formatted date.

    @Test
    fun `old buggy write format (space-separated, no zone) does not survive the new read side`() {
        val fixed = Date.from(Instant.parse("2026-08-08T14:30:00Z"))
        val oldBuggyWrite = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(fixed)

        val displayed = formatLastSyncForDisplay(oldBuggyWrite)

        // formatIsoForDisplay's documented fallback for unparseable input is the original
        // string, not a crash — but it is NOT the pretty "dd/MM/yyyy hh:mm a" shape the old
        // code produced, proving the two implementations are not silently equivalent.
        assertEquals(oldBuggyWrite, displayed)
        val directFormat = AppTime.formatForDisplay(
            fixed.toInstant(),
            AppTime.Formats.DATE_TIME_12H
        )
        assertNotEquals(directFormat, displayed)
    }

    // endregion
}
