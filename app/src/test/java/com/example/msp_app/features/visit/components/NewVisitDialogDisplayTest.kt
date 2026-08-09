package com.example.msp_app.features.visit.components

import java.time.LocalDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TDD suite for Task 9 of the fechas/AppTime migration — [formatRescheduleDateTime], extracted
 * from `NewVisitDialog.updateNoteWithDateTime` (previously the legacy date util's `formatLocalDateTime`).
 *
 * [dateTime] is a naive `LocalDateTime` built from the date/time pickers, with no zone
 * attached at the call site; [formatRescheduleDateTime] now explicitly anchors it in
 * `BUSINESS_ZONE` before formatting, matching how the rest of the app treats picker-selected
 * wall-clock values — never the device zone. The visible pattern/locale (`dd/MM/yyyy HH:mm`,
 * `es-MX`) are unchanged from the old legacy date util's `formatLocalDateTime`; since the value round-
 * trips through the SAME zone (business zone in, business zone out) and Mexico no longer
 * observes DST, the displayed string is identical to before for any date this app will see.
 */
class NewVisitDialogDisplayTest {

    @Test
    fun `formats a wall-clock date-time with the dd-MM-yyyy HH-mm pattern`() {
        val dateTime = LocalDateTime.of(2026, 4, 15, 14, 30)
        assertEquals("15/04/2026 14:30", formatRescheduleDateTime(dateTime))
    }

    @Test
    fun `formats midnight (00-00 por defecto when no time is picked)`() {
        val dateTime = LocalDateTime.of(2026, 4, 15, 0, 0)
        assertEquals("15/04/2026 00:00", formatRescheduleDateTime(dateTime))
    }

    @Test
    fun `formats a late-evening wall-clock time without rolling to the next calendar day`() {
        val dateTime = LocalDateTime.of(2026, 4, 15, 23, 45)
        assertEquals("15/04/2026 23:45", formatRescheduleDateTime(dateTime))
    }

    @Test
    fun `is independent of the device default TimeZone (BUSINESS_ZONE is explicit, never systemDefault)`() {
        val original = TimeZone.getDefault()
        try {
            val dateTime = LocalDateTime.of(2026, 4, 15, 14, 30)

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = formatRescheduleDateTime(dateTime)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = formatRescheduleDateTime(dateTime)

            assertEquals(underUtc, underTijuana)
            assertEquals("15/04/2026 14:30", underUtc)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
