package com.example.msp_app.core.utils

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 12 (fechas/AppTime migration, bug #9) — [PdfGenerator.printedAtLabel] is the testable
 * seam extracted from the three `SimpleDateFormat(..., Locale.getDefault()).format(Date())`
 * sites in [PdfGenerator] (`generatePdfFromLines`, `generateWarehouseInventoryPdf`,
 * `generateDailyReportPdf`) — none of those functions are unit-testable directly (Android
 * `PdfDocument`/`Canvas`), so this pure wrapper carries the coverage instead.
 */
class PdfGeneratorPrintedAtLabelTest {

    @Test
    fun `formats with the 24h pattern by default, matching business zone`() {
        val fixed = Instant.parse("2026-08-08T14:30:00Z") // 08:30 CDMX
        val clock = FakeClock(fixed)

        val result = PdfGenerator.printedAtLabel(clock)

        assertEquals("08/08/2026 08:30", result)
        assertEquals(AppTime.formatForDisplay(fixed, AppTime.Formats.DATE_TIME_24H), result)
    }

    @Test
    fun `formats with an explicit 12h pattern`() {
        val fixed = Instant.parse("2026-08-08T14:30:00Z")
        val clock = FakeClock(fixed)

        val result = PdfGenerator.printedAtLabel(clock, AppTime.Formats.DATE_TIME_12H)

        // Spanish locale emits "a. m."/"p. m." in newer JDKs, "AM"/"PM" in older — only the
        // digits are asserted exactly, matching the AppTimeTest convention.
        assertTrue("got: $result", result.startsWith("08/08/2026 08:30"))
    }

    @Test
    fun `is independent of the device default Locale`() {
        val original = Locale.getDefault()
        try {
            val clock = FakeClock(Instant.parse("2026-08-08T14:30:00Z"))

            Locale.setDefault(Locale.US)
            val underUs = PdfGenerator.printedAtLabel(clock)

            Locale.setDefault(Locale("ar"))
            val underAr = PdfGenerator.printedAtLabel(clock)

            assertEquals(underUs, underAr)
            assertEquals("08/08/2026 08:30", underUs)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `reflects clock advancement`() {
        val clock = FakeClock(Instant.parse("2026-08-08T14:30:00Z"))

        val before = PdfGenerator.printedAtLabel(clock)
        clock.advanceHours(1)
        val after = PdfGenerator.printedAtLabel(clock)

        assertEquals("08/08/2026 08:30", before)
        assertEquals("08/08/2026 09:30", after)
    }
}
