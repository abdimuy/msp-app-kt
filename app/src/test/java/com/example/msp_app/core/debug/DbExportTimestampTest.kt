package com.example.msp_app.core.debug

import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 12 (fechas/AppTime migration, bug #9) — [dbExportTimestamp] is the testable seam
 * extracted from `DbExportManager.createTempCopy`'s
 * `SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())`.
 * [DbExportManager] itself is not unit-tested directly in this codebase (Firebase/Context
 * dependencies, no fakes available — fakes-only test policy).
 */
class DbExportTimestampTest {

    @Test
    fun `formats as yyyyMMdd_HHmmss in business zone`() {
        val fixed = Instant.parse("2026-08-08T14:30:05Z") // 08:30:05 CDMX
        val clock = FakeClock(fixed)

        val result = dbExportTimestamp(clock)

        assertEquals("20260808_083005", result)
    }

    @Test
    fun `is independent of the device default Locale`() {
        // Legacy SimpleDateFormat/Calendar picks a locale-specific chronology (e.g. Thai
        // Buddhist era, +543 years) purely from Locale.getDefault(); java.time's
        // DateTimeFormatter defaults to ISO chronology regardless of locale, which is exactly
        // the property this migration relies on.
        val original = Locale.getDefault()
        try {
            val clock = FakeClock(Instant.parse("2026-08-08T14:30:05Z"))

            Locale.setDefault(Locale.US)
            val underUs = dbExportTimestamp(clock)

            Locale.setDefault(Locale("th", "TH"))
            val underTh = dbExportTimestamp(clock)

            assertEquals(underUs, underTh)
            assertEquals("20260808_083005", underUs)
        } finally {
            Locale.setDefault(original)
        }
    }
}
