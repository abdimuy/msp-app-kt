package com.example.msp_app.features.home.components.homestartweeksection

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.features.sales.viewmodels.currentSalesLastSync
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 12 (fechas/AppTime migration) — the "última sincronización" round-trip between
 * [com.example.msp_app.features.sales.viewmodels.SalesViewModel.syncSales] (write, via
 * [currentSalesLastSync]) and `HomeStartWeekSection` (read, via [formatLastSyncForDisplay])
 * used to format/parse through two INDEPENDENT `SimpleDateFormat(..., Locale.getDefault())`
 * calls — one obtained at write time, one obtained again at read time. Bug #8.
 *
 * Region 4 below is the actual repro: it inlines the exact pre-fix code (not the new
 * `AppTime`-based implementation) and demonstrates it silently produces a WRONG date — no
 * exception, no crash, just 543 years off — when the device default locale changes calendar
 * system between write and read. Regions 1-3 characterize the NEW implementation's behavior
 * (correctness, locale-independence, graceful degradation of an old persisted value); they do
 * NOT themselves reproduce the old failure, which is why the genuine repro lives in region 4.
 */
class LastSyncDisplayTest {

    // region — 1. NEW implementation: round trip, fixed clock, default locale

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

    // region — 2. NEW implementation: independent of Locale.getDefault() on either side —
    // these assert the FIX's property, not the OLD bug's failure (see region 4 for that).

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

    // endregion

    // region — 3. NEW implementation: an old persisted value (legacy format, pre-migration)
    // degrades gracefully instead of crashing. This is a format-SHAPE compatibility test
    // (space separator, no zone/offset — not one of the 4 shapes AppTime.parseWireFormat
    // accepts), independent of the locale-driven mechanism of bug #8 covered in region 4.

    @Test
    fun `legacy persisted value (old space-separated format, no zone) degrades to the raw string, no crash`() {
        val fixed = Date.from(Instant.parse("2026-08-08T14:30:00Z"))
        val legacyPersistedValue = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(fixed)

        val displayed = formatLastSyncForDisplay(legacyPersistedValue)

        // formatIsoForDisplay's documented fallback for unparseable input is the original
        // string, not a crash — but it is NOT the pretty "dd/MM/yyyy hh:mm a" shape the old
        // code produced, proving the two implementations are not silently equivalent. The
        // stale value self-heals on the next successful sync, which persists the new wire
        // format.
        assertEquals(legacyPersistedValue, displayed)
        val directFormat = AppTime.formatForDisplay(
            fixed.toInstant(),
            AppTime.Formats.DATE_TIME_12H
        )
        assertNotEquals(directFormat, displayed)
    }

    // endregion

    // region — 4. Genuine bug #8 repro: the OLD double-SimpleDateFormat round trip, inlined
    // verbatim (not the new AppTime-based code), really does break — silently, no exception —
    // when the device's default locale switches calendar system between write and read. Paired
    // with a test proving the NEW code does not break under the identical scenario.
    //
    // `Locale("th", "TH", "TH")` is the legacy 3-arg JDK locale that switches
    // `java.text.SimpleDateFormat`/`java.util.Calendar` to the Thai Buddhist calendar
    // (Gregorian year + 543). Verified interactively on the project JDK
    // (`Android Studio.app/Contents/jbr`, the same JDK this Gradle gate runs under):
    // formatting "2026-08-08 14:30:00" under `Locale.US` and re-parsing the SAME string under
    // `Locale("th","TH","TH")` yields year 1483, not 2026 — `SimpleDateFormat.parse` does not
    // throw; it silently reinterprets the numeral "2026" as a Buddhist-Era year.
    // (`Locale.forLanguageTag("ar-SA-u-ca-islamic")` was also tried and does NOT reproduce the
    // failure on this JDK — the legacy `Calendar.getInstance(Locale)` path ignores the Unicode
    // `-u-ca-` extension — so the Thai legacy locale is the one used here.)

    @Test
    fun `OLD SimpleDateFormat round trip is silently wrong by 543 years when default locale switches calendar between write and read`() {
        val original = Locale.getDefault()
        try {
            val fixed = Date.from(Instant.parse("2026-08-08T14:30:00Z"))
            val wirePattern = "yyyy-MM-dd HH:mm:ss"

            // OLD write side, inlined verbatim from the pre-fix SalesViewModel.syncSales:
            // SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()).
            Locale.setDefault(Locale.US)
            val oldWritten = SimpleDateFormat(wirePattern, Locale.getDefault()).format(fixed)

            // Device default locale changes between sync and the next screen render — the
            // real-world trigger for bug #8 (user changes system language/region).
            Locale.setDefault(Locale("th", "TH", "TH"))

            // OLD read side, inlined verbatim from the pre-fix HomeStartWeekSection: parse
            // with a FRESH SimpleDateFormat built from the (now different) Locale.getDefault().
            val oldParsed = SimpleDateFormat(wirePattern, Locale.getDefault()).parse(oldWritten)
            requireNotNull(oldParsed) { "old code did not even throw a ParseException here" }

            val oldParsedYear = oldParsed.toInstant().atZone(ZoneOffset.UTC).year

            // No exception was thrown — the corruption is silent.
            assertEquals(1483, oldParsedYear)
            assertNotEquals(2026, oldParsedYear)
            assertFalse(
                "OLD code must NOT round-trip correctly under this locale switch — that IS bug #8",
                oldParsed.toInstant() == fixed.toInstant()
            )
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `NEW currentSalesLastSync plus formatLastSyncForDisplay survives the identical locale switch that breaks the OLD code`() {
        val original = Locale.getDefault()
        try {
            val fixed = Instant.parse("2026-08-08T14:30:00Z")

            // Same write-then-locale-switch-then-read shape as the OLD-code repro above.
            Locale.setDefault(Locale.US)
            val persisted = currentSalesLastSync(FakeClock(fixed))

            Locale.setDefault(Locale("th", "TH", "TH"))
            val displayed = formatLastSyncForDisplay(persisted)

            assertTrue("got: $displayed", displayed.startsWith("08/08/2026 08:30"))
        } finally {
            Locale.setDefault(original)
        }
    }

    // endregion
}
