package com.example.msp_app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
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
    fun `parseWireFormatOrNull returns null for malformed`() {
        assertNull(AppTime.parseWireFormatOrNull("garbage"))
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
