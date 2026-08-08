package com.example.msp_app.features.sales.screens

import com.example.msp_app.core.testing.time.FakeClock
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD suite for Task 9 of the fechas/AppTime migration — [formatElapsedTime], the "Hace X"
 * elapsed-time label for the last payment on a sale's overdue-payments card.
 *
 * **Zone-consistency audit finding (carried from Task 4, closed here):** before this change,
 * `lastPaymentDateTime` came from `Instant.parse(...).atZone(ZoneId.systemDefault())` (device
 * zone) and `now` came from a bare `LocalDateTime.now()` (also implicitly device zone). Both
 * operands WERE already in the same zone — so the elapsed *duration* was never wrong on a
 * given device — but the whole calculation silently tracked the device's configured timezone
 * rather than the fixed business zone, and had no injectable clock for tests. This suite pins
 * the new behaviour: both operands now go through [com.example.msp_app.core.common.time.AppTime]
 * business zone conversions, and `now` comes from an injectable [FakeClock], making the result
 * deterministic and independent of the device's zone.
 *
 * **Leniency improvement (documented, not accidental):** `AppTime.parseWireFormatOrNull` accepts
 * a strictly wider set of ISO shapes than the old hand-rolled `Instant.parse` /
 * `LocalDate.parse` fallback chain — notably a zoneless `"yyyy-MM-ddTHH:mm:ss"` string (no `Z`,
 * no offset), which the old code could not parse in either branch and silently rendered as
 * "Sin datos". See the dedicated test below pinning the new, more permissive behaviour.
 */
class SaleDetailsScreenElapsedTimeTest {

    @Test
    fun `returns Sin datos for a malformed ISO string, no crash`() {
        val clock = FakeClock.at("2026-04-15T12:00:00Z")
        assertEquals("Sin datos", formatElapsedTime("no-es-una-fecha", clock))
    }

    @Test
    fun `returns Sin datos for a blank string`() {
        val clock = FakeClock.at("2026-04-15T12:00:00Z")
        assertEquals("Sin datos", formatElapsedTime("", clock))
    }

    @Test
    fun `renders a few seconds for a payment made moments ago`() {
        val clock = FakeClock.at("2026-04-15T12:00:30Z")
        assertEquals("Hace unos segundos", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders singular minute`() {
        val clock = FakeClock.at("2026-04-15T12:01:00Z")
        assertEquals("Hace 1 minuto", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders plural minutes`() {
        val clock = FakeClock.at("2026-04-15T12:10:00Z")
        assertEquals("Hace 10 minutos", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders singular hour`() {
        val clock = FakeClock.at("2026-04-15T13:00:00Z")
        assertEquals("Hace 1 hora", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders plural hours`() {
        val clock = FakeClock.at("2026-04-15T17:00:00Z")
        assertEquals("Hace 5 horas", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders singular day`() {
        val clock = FakeClock.at("2026-04-16T12:00:00Z")
        assertEquals("Hace 1 día", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders plural days`() {
        val clock = FakeClock.at("2026-04-18T12:00:00Z")
        assertEquals("Hace 3 días", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders singular week`() {
        val clock = FakeClock.at("2026-04-25T12:00:00Z") // 10 days later -> 10/7 = 1 week
        assertEquals("Hace 1 semana", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders plural weeks`() {
        val clock = FakeClock.at("2026-05-06T12:00:00Z") // 21 days later -> 21/7 = 3 weeks
        assertEquals("Hace 3 semanas", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders singular month`() {
        val clock = FakeClock.at("2026-05-20T12:00:00Z") // 35 days later -> 35/30 = 1 month
        assertEquals("Hace 1 mes", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders plural months`() {
        val clock = FakeClock.at("2026-10-15T12:00:00Z") // 183 days later -> 183/30 = 6 months
        assertEquals("Hace 6 meses", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    @Test
    fun `renders singular year`() {
        val clock = FakeClock.at("2027-04-25T12:00:00Z") // 375 days later -> 375/365 = 1 year
        assertEquals("Hace 1 año", formatElapsedTime("2026-04-15T12:00:00Z", clock))
    }

    // region — zone-consistency

    @Test
    fun `23-00 CDMX boundary - elapsed is a few minutes, not confused by the next-day UTC date`() {
        // Payment at 23:30 CDMX 2026-04-15 == 2026-04-16T05:30:00Z; "now" 15 minutes later,
        // same CDMX business evening, 2026-04-16T05:45:00Z.
        val clock = FakeClock.at("2026-04-16T05:45:00Z")
        assertEquals("Hace 15 minutos", formatElapsedTime("2026-04-16T05:30:00Z", clock))
    }

    @Test
    fun `is independent of the device default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val clock = FakeClock.at("2026-04-18T12:00:00Z")

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = formatElapsedTime("2026-04-15T12:00:00Z", clock)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = formatElapsedTime("2026-04-15T12:00:00Z", clock)

            assertEquals(underUtc, underTijuana)
            assertEquals("Hace 3 días", underUtc)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // endregion

    // region — leniency improvement: zoneless "yyyy-MM-ddTHH:mm:ss" now parses (business zone)

    @Test
    fun `zoneless ISO date-time (no Z, no offset) now parses instead of falling back to Sin datos`() {
        // The old Instant-parse-then-LocalDate-parse chain could not handle this shape in
        // either branch and always returned "Sin datos". AppTime.parseWireFormatOrNull treats
        // it as a business-zone wall-clock value (legacy-lenient shape).
        val clock = FakeClock.at("2026-04-15T18:00:00Z") // 12:00 CDMX
        val result = formatElapsedTime("2026-04-15T10:00:00", clock) // 10:00 CDMX, no zone
        assertTrue("expected a real elapsed string, got: $result", result != "Sin datos")
        assertEquals("Hace 2 horas", result)
    }

    // endregion
}
