package com.example.msp_app.core.time

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeClockTest {

    @Test
    fun `now returns initial value`() {
        val clock = FakeClock(Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), clock.now())
    }

    @Test
    fun `setNow replaces current`() {
        val clock = FakeClock()
        clock.setNow(Instant.parse("2030-12-31T23:59:59Z"))
        assertEquals(Instant.parse("2030-12-31T23:59:59Z"), clock.now())
    }

    @Test
    fun `advance moves forward by duration`() {
        val clock = FakeClock(Instant.parse("2026-01-01T00:00:00Z"))
        clock.advance(Duration.ofHours(5))
        assertEquals(Instant.parse("2026-01-01T05:00:00Z"), clock.now())
    }

    @Test
    fun `advanceDays respects calendar days`() {
        val clock = FakeClock(Instant.parse("2026-01-01T00:00:00Z"))
        clock.advanceDays(30)
        assertEquals(Instant.parse("2026-01-31T00:00:00Z"), clock.now())
    }

    @Test
    fun `at builds clock from ISO string`() {
        val clock = FakeClock.at("2026-04-15T18:30:00-06:00")
        // 12:30 CDMX + 6h == 18:30 + 6 == err wait, 12:30 -06:00 == 18:30 UTC
        // Actually "18:30:00-06:00" wall = 18:30 at -06 offset = 00:30 UTC next day
        assertEquals(Instant.parse("2026-04-16T00:30:00Z"), clock.now())
    }
}
