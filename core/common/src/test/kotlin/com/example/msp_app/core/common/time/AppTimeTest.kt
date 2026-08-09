package com.example.msp_app.core.common.time

import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.UnsupportedTemporalTypeException
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTimeTest {

    // region — Wire format

    @Test
    fun `toWireFormat emits ISO 8601 UTC with Z`() {
        val instant = Instant.parse("2026-04-15T18:30:00Z")
        assertEquals("2026-04-15T18:30:00Z", AppTime.toWireFormat(instant))
    }

    @Test
    fun `parseWireFormat accepts canonical UTC`() {
        val i = AppTime.parseWireFormat("2026-04-15T18:30:00Z")
        assertEquals(Instant.parse("2026-04-15T18:30:00Z"), i)
    }

    @Test
    fun `parseWireFormat accepts fractional seconds`() {
        val i = AppTime.parseWireFormat("2026-04-15T18:30:00.123Z")
        assertEquals(Instant.parse("2026-04-15T18:30:00.123Z"), i)
    }

    @Test
    fun `parseWireFormat accepts microsecond fraction (Go RFC3339Nano, 6 digits)`() {
        val i = AppTime.parseWireFormat("2026-04-15T18:30:00.123456Z")
        assertEquals(Instant.parse("2026-04-15T18:30:00.123456Z"), i)
    }

    @Test
    fun `parseWireFormat accepts nanosecond fraction (Go RFC3339Nano, 9 digits)`() {
        val i = AppTime.parseWireFormat("2026-04-15T18:30:00.123456789Z")
        assertEquals(Instant.parse("2026-04-15T18:30:00.123456789Z"), i)
    }

    @Test
    fun `parseWireFormat accepts offset form`() {
        // -06:00 at wall 12:30 = 18:30 UTC
        val i = AppTime.parseWireFormat("2026-04-15T12:30:00-06:00")
        assertEquals(Instant.parse("2026-04-15T18:30:00Z"), i)
    }

    @Test
    fun `parseWireFormat accepts positive offset form`() {
        val i = AppTime.parseWireFormat("2026-04-15T20:30:00+02:00")
        assertEquals(Instant.parse("2026-04-15T18:30:00Z"), i)
    }

    @Test
    fun `parseWireFormat accepts explicit zero offset form`() {
        val i = AppTime.parseWireFormat("2026-04-15T18:30:00+00:00")
        assertEquals(Instant.parse("2026-04-15T18:30:00Z"), i)
    }

    @Test
    fun `parseWireFormat accepts the real backend FECHA_HORA_CREACION shape`() {
        // Documented in docs/standards/timezones.md — legacy endpoint, offset -06:00 with millis.
        val i = AppTime.parseWireFormat("2026-04-22T19:43:56.000-06:00")
        assertEquals(Instant.parse("2026-04-23T01:43:56Z"), i)
    }

    @Test
    fun `parseWireFormat accepts zoneless datetime interpreting as business zone`() {
        // 12:30 local in CDMX (UTC-6) == 18:30 UTC
        val i = AppTime.parseWireFormat("2026-04-15T12:30:00")
        assertEquals(Instant.parse("2026-04-15T18:30:00Z"), i)
    }

    @Test
    fun `parseWireFormat accepts date-only interpreting as business midnight`() {
        // midnight CDMX = 06:00 UTC
        val i = AppTime.parseWireFormat("2026-04-15")
        assertEquals(Instant.parse("2026-04-15T06:00:00Z"), i)
    }

    @Test(expected = DateTimeParseException::class)
    fun `parseWireFormat rejects blank`() {
        AppTime.parseWireFormat("")
    }

    @Test(expected = DateTimeParseException::class)
    fun `parseWireFormat rejects malformed`() {
        AppTime.parseWireFormat("not-a-date")
    }

    @Test
    fun `parseWireFormatOrNull returns null for null`() {
        assertNull(AppTime.parseWireFormatOrNull(null))
    }

    @Test
    fun `parseWireFormatOrNull returns null for blank`() {
        assertNull(AppTime.parseWireFormatOrNull("   "))
    }

    @Test
    fun `parseWireFormatOrNull returns null for empty string`() {
        assertNull(AppTime.parseWireFormatOrNull(""))
    }

    @Test
    fun `parseWireFormatOrNull returns null for malformed`() {
        assertNull(AppTime.parseWireFormatOrNull("garbage"))
    }

    @Test
    fun `parseWireFormatOrNull returns null for an invalid calendar date`() {
        // Day-first, not ISO, and month 13 doesn't exist — must degrade to null, never throw.
        assertNull(AppTime.parseWireFormatOrNull("31/13/2026"))
    }

    @Test
    fun `parseWireFormatOrNull returns Instant for valid`() {
        assertNotNull(AppTime.parseWireFormatOrNull("2026-04-15T18:30:00Z"))
    }

    @Test
    fun `wire date round-trips`() {
        val d = LocalDate.of(2026, 4, 15)
        assertEquals("2026-04-15", AppTime.toWireDate(d))
        assertEquals(d, AppTime.parseWireDateOrNull("2026-04-15"))
    }

    @Test
    fun `parseWireDateOrNull returns null for malformed`() {
        assertNull(AppTime.parseWireDateOrNull("2026/04/15"))
        assertNull(AppTime.parseWireDateOrNull(null))
        assertNull(AppTime.parseWireDateOrNull(""))
    }

    @Test
    fun `toWireFormat then parseWireFormat round-trips exactly without fraction`() {
        val i = Instant.parse("2026-04-15T18:30:00Z")
        assertEquals(i, AppTime.parseWireFormat(AppTime.toWireFormat(i)))
    }

    @Test
    fun `toWireFormat then parseWireFormat round-trips exactly with millis`() {
        val i = Instant.parse("2026-04-15T18:30:00.123Z")
        assertEquals(i, AppTime.parseWireFormat(AppTime.toWireFormat(i)))
    }

    @Test
    fun `toWireFormat then parseWireFormat round-trips exactly with microseconds`() {
        val i = Instant.parse("2026-04-15T18:30:00.123456Z")
        assertEquals(i, AppTime.parseWireFormat(AppTime.toWireFormat(i)))
    }

    @Test
    fun `toWireFormat then parseWireFormat round-trips exactly with nanoseconds`() {
        val i = Instant.parse("2026-04-15T18:30:00.123456789Z")
        assertEquals(i, AppTime.parseWireFormat(AppTime.toWireFormat(i)))
    }

    // endregion

    // region — plusOnWire (replaces the legacy date util's addToIsoDate, bug #3)

    @Test
    fun `plusOnWire adds days crossing a month boundary`() {
        val result = AppTime.plusOnWire("2026-04-29T10:00:00Z", 3, ChronoUnit.DAYS)
        assertEquals("2026-05-02T10:00:00Z", result)
    }

    @Test
    fun `plusOnWire adds seconds crossing a year boundary`() {
        val result = AppTime.plusOnWire("2026-12-31T23:59:59Z", 2, ChronoUnit.SECONDS)
        assertEquals("2027-01-01T00:00:01Z", result)
    }

    @Test
    fun `plusOnWire subtracts days with a negative amount`() {
        val result = AppTime.plusOnWire("2026-05-02T10:00:00Z", -3, ChronoUnit.DAYS)
        assertEquals("2026-04-29T10:00:00Z", result)
    }

    @Test
    fun `plusOnWire preserves fractional-second precision`() {
        val result = AppTime.plusOnWire("2026-04-15T18:30:00.123456Z", 1, ChronoUnit.HOURS)
        assertEquals("2026-04-15T19:30:00.123456Z", result)
    }

    @Test
    fun `plusOnWire does not drift when the input carries a non-zero offset`() {
        // Legacy shape (-06:00). The old legacy date util's addToIsoDate only worked by accident for offset
        // zero; plusOnWire operates on the parsed Instant so any offset is safe.
        val result = AppTime.plusOnWire("2026-04-22T19:43:56.000-06:00", 1, ChronoUnit.DAYS)
        assertEquals("2026-04-24T01:43:56Z", result)
    }

    @Test(expected = UnsupportedTemporalTypeException::class)
    fun `plusOnWire rejects calendar units like MONTHS since it operates on Instant`() {
        // Instant has no notion of a calendar month (it is not zone-aware), so java.time
        // rejects ChronoUnit.MONTHS/YEARS at runtime rather than silently doing the wrong
        // thing. Callers that need "N months from date X" must work in LocalDate, not
        // plusOnWire — documented here so nobody assumes calendar units work.
        AppTime.plusOnWire("2026-04-15T10:00:00Z", 1, ChronoUnit.MONTHS)
    }

    // endregion

    // region — Business zone conversions (THE bug class)

    @Test
    fun `toBusinessDate handles late-night local = UTC next day`() {
        // 23:30 CDMX on 2026-04-15 == 05:30 UTC 2026-04-16
        val lateNightLocal = Instant.parse("2026-04-16T05:30:00Z")
        assertEquals(LocalDate.of(2026, 4, 15), AppTime.toBusinessDate(lateNightLocal))
    }

    @Test
    fun `toBusinessDate handles early-morning local = same UTC day`() {
        // 00:30 CDMX on 2026-04-16 == 06:30 UTC 2026-04-16
        val earlyLocal = Instant.parse("2026-04-16T06:30:00Z")
        assertEquals(LocalDate.of(2026, 4, 16), AppTime.toBusinessDate(earlyLocal))
    }

    @Test
    fun `todayInBusinessZone uses business zone not UTC`() {
        // FakeClock at 03:00 UTC 2026-04-16 → 21:00 CDMX of 2026-04-15
        val clock = FakeClock.at("2026-04-16T03:00:00Z")
        assertEquals(LocalDate.of(2026, 4, 15), AppTime.todayInBusinessZone(clock))
    }

    @Test
    fun `nowInBusinessZone with a fixed AppClock gives the exact business wall-clock time`() {
        // FakeClock at 03:15 UTC 2026-04-16 → 21:15 CDMX of 2026-04-15 (not the host's "now").
        val clock = FakeClock.at("2026-04-16T03:15:00Z")
        val now = AppTime.nowInBusinessZone(clock)
        assertEquals(LocalDate.of(2026, 4, 15), now.toLocalDate())
        assertEquals(21, now.hour)
        assertEquals(15, now.minute)
    }

    @Test
    fun `startOfDay returns 06 UTC for CDMX midnight`() {
        val d = LocalDate.of(2026, 4, 15)
        assertEquals(Instant.parse("2026-04-15T06:00:00Z"), AppTime.startOfDay(d))
    }

    @Test
    fun `startOfNextDay is 24h after startOfDay outside DST transitions`() {
        val d = LocalDate.of(2026, 4, 15)
        assertEquals(Instant.parse("2026-04-16T06:00:00Z"), AppTime.startOfNextDay(d))
    }

    // endregion

    // region — Half-open day range [startOfDay, startOfNextDay)

    @Test
    fun `23-59-59-999 CDMX of day D is still inside the range for D`() {
        val d = LocalDate.of(2026, 4, 15)
        val lastInstantOfDay = Instant.parse("2026-04-16T05:59:59.999Z") // 23:59:59.999 CDMX
        assertTrue(!lastInstantOfDay.isBefore(AppTime.startOfDay(d)))
        assertTrue(lastInstantOfDay.isBefore(AppTime.startOfNextDay(d)))
    }

    @Test
    fun `00-00-00-001 CDMX of day D+1 falls outside the range for D`() {
        val d = LocalDate.of(2026, 4, 15)
        val firstInstantOfNextDay = Instant.parse(
            "2026-04-16T06:00:00.001Z"
        ) // 00:00:00.001 CDMX (16-abr)
        assertFalse(firstInstantOfNextDay.isBefore(AppTime.startOfNextDay(d)))
    }

    @Test
    fun `exact CDMX midnight equals startOfDay, not startOfDay minus one`() {
        val d = LocalDate.of(2026, 4, 16)
        val exactMidnight = Instant.parse("2026-04-16T06:00:00.000Z")
        assertEquals(AppTime.startOfDay(d), exactMidnight)
    }

    // endregion

    // region — Month / year boundaries

    @Test
    fun `toBusinessDate keeps year-end sale on the correct calendar year`() {
        // 23:30 CDMX 31-dic-2026 == 05:30 UTC 01-ene-2027 — must NOT bleed into 2027 business-wise.
        val yearEndSale = Instant.parse("2027-01-01T05:30:00Z")
        assertEquals(LocalDate.of(2026, 12, 31), AppTime.toBusinessDate(yearEndSale))
    }

    @Test
    fun `startOfNextDay rolls December 31 into January 1 of the next year`() {
        val d = LocalDate.of(2026, 12, 31)
        assertEquals(Instant.parse("2027-01-01T06:00:00Z"), AppTime.startOfNextDay(d))
    }

    @Test
    fun `startOfDay and toBusinessDate agree on a leap-year Feb 29`() {
        // 2028 is a leap year.
        val leapDay = LocalDate.of(2028, 2, 29)
        val start = AppTime.startOfDay(leapDay)
        assertEquals(leapDay, AppTime.toBusinessDate(start))
        assertEquals(
            LocalDate.of(2028, 3, 1),
            AppTime.toBusinessDate(AppTime.startOfNextDay(leapDay))
        )
    }

    @Test
    fun `plusOnWire one day from Feb 28 lands on Feb 29 in a leap year but Mar 1 otherwise`() {
        val leapResult = AppTime.plusOnWire("2028-02-28T12:00:00Z", 1, ChronoUnit.DAYS)
        assertEquals("2028-02-29T12:00:00Z", leapResult)

        val nonLeapResult = AppTime.plusOnWire("2026-02-28T12:00:00Z", 1, ChronoUnit.DAYS)
        assertEquals("2026-03-01T12:00:00Z", nonLeapResult)
    }

    // endregion

    // region — DST historical (Mexico observed DST nationally through 2022)

    @Test
    fun `converting instants across the 2021 fall-back transition does not throw and keeps the calendar day`() {
        // Last Sunday of October 2021 = Oct 31; clocks in CDMX moved back 02:00 -> 01:00 CDT/CST.
        // Both sides of the transition are still calendar day Oct 31 in business zone.
        val beforeTransition = Instant.parse("2021-10-31T05:00:00Z") // ~00:00 CDT (-05:00)
        val afterTransition = Instant.parse("2021-10-31T09:00:00Z") // ~03:00 CST (-06:00)

        assertEquals(LocalDate.of(2021, 10, 31), AppTime.toBusinessDate(beforeTransition))
        assertEquals(LocalDate.of(2021, 10, 31), AppTime.toBusinessDate(afterTransition))
    }

    @Test
    fun `legacy zoneless parse across the 2021 spring-forward gap does not throw`() {
        // First Sunday of April 2021 = Apr 4; local clocks jumped 02:00 -> 03:00 (the 02:xx hour
        // does not exist that day). A legacy no-zone wire string landing inside the gap must
        // still resolve to a valid Instant, never throw.
        val resolved = AppTime.parseWireFormat("2021-04-04T02:30:00")

        // Whatever the JDK's gap-resolution strategy, the result must land at/after the local
        // 03:00 wall-clock instant that starts DST that day — never inside the non-existent hour.
        val startOfDstThatDay = Instant.parse("2021-04-04T08:00:00Z") // 02:00 CST == 08:00 UTC
        assertTrue(!resolved.isBefore(startOfDstThatDay))
    }

    // endregion

    // region — Queries (the daily-report bug)

    @Test
    fun `isToday returns true for late-evening sale on same business date`() {
        // Clock: 18:00 CDMX of 2026-04-15 (00:00 UTC next day)
        val clock = FakeClock.at("2026-04-16T00:00:00Z")
        // Sale: 23:30 CDMX of 2026-04-15 — UTC is already 2026-04-16 05:30
        val sale = Instant.parse("2026-04-16T05:30:00Z")
        assertTrue(AppTime.isToday(sale, clock))
    }

    @Test
    fun `isToday returns false for yesterday sale on today's clock`() {
        val clock = FakeClock.at("2026-04-16T14:00:00Z") // 08:00 CDMX today
        val yesterday = Instant.parse("2026-04-15T14:00:00Z") // 08:00 CDMX yesterday
        assertFalse(AppTime.isToday(yesterday, clock))
    }

    @Test
    fun `isOn matches exact business date`() {
        val t = Instant.parse("2026-04-16T05:30:00Z") // 23:30 CDMX 2026-04-15
        assertTrue(AppTime.isOn(t, LocalDate.of(2026, 4, 15)))
        assertFalse(AppTime.isOn(t, LocalDate.of(2026, 4, 16)))
    }

    @Test
    fun `isThisWeek covers Monday through Sunday in business zone`() {
        // 2026-04-15 is Wednesday. Monday of that week = 2026-04-13.
        val clock = FakeClock.at("2026-04-15T12:00:00-06:00")

        val monday = Instant.parse("2026-04-13T06:00:01Z") // 00:00:01 CDMX Mon
        val sunday = Instant.parse("2026-04-20T05:59:59Z") // 23:59:59 CDMX Sun
        val lastWeekSunday = Instant.parse("2026-04-13T05:59:59Z") // 23:59:59 CDMX Sun 12-Apr
        val nextMonday = Instant.parse("2026-04-20T06:00:01Z") // 00:00:01 CDMX Mon 20

        assertTrue(AppTime.isThisWeek(monday, clock))
        assertTrue(AppTime.isThisWeek(sunday, clock))
        assertFalse(AppTime.isThisWeek(lastWeekSunday, clock))
        assertFalse(AppTime.isThisWeek(nextMonday, clock))
    }

    // endregion

    // region — Display formatting

    @Test
    fun `formatForDisplay uses business zone`() {
        // 18:30 UTC == 12:30 CDMX
        val i = Instant.parse("2026-04-15T18:30:00Z")
        assertEquals("15/04/2026 12:30", AppTime.formatForDisplay(i))
    }

    @Test
    fun `formatForDisplay with 12h pattern`() {
        val i = Instant.parse("2026-04-15T18:30:00Z")
        val result = AppTime.formatForDisplay(i, AppTime.Formats.DATE_TIME_12H)
        // 12:30 PM CDMX — spanish locale emits "p. m." in newer JDKs and "PM" in older.
        assertTrue("got: $result", result.startsWith("15/04/2026 12:30"))
    }

    @Test
    fun `formatDate uses short pattern by default`() {
        assertEquals("15/04/2026", AppTime.formatDate(LocalDate.of(2026, 4, 15)))
    }

    @Test
    fun `formatIsoForDisplay parses and formats in one step`() {
        val result = AppTime.formatIsoForDisplay("2026-04-15T18:30:00Z")
        assertEquals("15/04/2026 12:30", result)
    }

    @Test
    fun `formatIsoForDisplay returns empty for null or blank`() {
        assertEquals("", AppTime.formatIsoForDisplay(null))
        assertEquals("", AppTime.formatIsoForDisplay(""))
        assertEquals("", AppTime.formatIsoForDisplay("   "))
    }

    @Test
    fun `formatIsoForDisplay returns original string on malformed input`() {
        assertEquals("garbage", AppTime.formatIsoForDisplay("garbage"))
    }

    // endregion

    // region — Device-zone independence (the bug the legacy date util has and AppTime does not)

    @Test
    fun `AppTime results do not change with the JVM default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val instant = Instant.parse("2026-04-16T05:30:00Z") // 23:30 CDMX 2026-04-15
            val date = LocalDate.of(2026, 4, 15)
            val clock = FakeClock.at("2026-04-16T03:00:00Z")

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val resultsUnderUtc = collectZoneSensitiveResults(instant, date, clock)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val resultsUnderTijuana = collectZoneSensitiveResults(instant, date, clock)

            assertEquals(resultsUnderUtc, resultsUnderTijuana)
            // Pin the actual expected value too — this is the business-zone-correct result,
            // not an artifact of whichever device zone happened to run the test.
            assertEquals(
                ZoneIndependentResults(
                    businessDate = LocalDate.of(2026, 4, 15),
                    startOfDay = Instant.parse("2026-04-15T06:00:00Z"),
                    display = "15/04/2026 23:30",
                    today = LocalDate.of(2026, 4, 15)
                ),
                resultsUnderUtc
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun collectZoneSensitiveResults(
        instant: Instant,
        date: LocalDate,
        clock: FakeClock
    ): ZoneIndependentResults = ZoneIndependentResults(
        businessDate = AppTime.toBusinessDate(instant),
        startOfDay = AppTime.startOfDay(date),
        display = AppTime.formatForDisplay(instant),
        today = AppTime.todayInBusinessZone(clock)
    )

    private data class ZoneIndependentResults(
        val businessDate: LocalDate,
        val startOfDay: Instant,
        val display: String,
        val today: LocalDate
    )

    // endregion

    // region — The bug from the daily report reproduced

    @Test
    fun `regression - sale at 1805 CDMX appears in today's report not tomorrow's`() {
        // Venta hecha a las 18:05 local del 15 de abril.
        // Con la logica vieja, Instant.now() == 00:05 UTC del 16-abr.
        // El filtro viejo usaba LocalDate.now() (local 15-abr) y startsWith("2026-04-15")
        // -> la venta con prefijo "2026-04-16" NO caia en el reporte del 15-abr.

        val saleTimestamp = Instant.parse("2026-04-16T00:05:00Z")

        // User abre el reporte a las 20:00 local del 15-abr
        val clock = FakeClock.at("2026-04-16T02:00:00Z")

        assertTrue(
            "Sale at 18:05 CDMX must appear in today's report",
            AppTime.isToday(saleTimestamp, clock)
        )
    }

    @Test
    fun `regression - yesterday's late sale does not bleed into today`() {
        // Misma venta 18:05 CDMX 15-abr
        val saleTimestamp = Instant.parse("2026-04-16T00:05:00Z")

        // Al día siguiente 09:00 local del 16-abr → reporte NO debe incluirla
        val clockNextDay = FakeClock.at("2026-04-16T15:00:00Z")

        assertFalse(
            "Yesterday's sale must not appear in next day's report",
            AppTime.isToday(saleTimestamp, clockNextDay)
        )
    }

    // endregion
}
