package com.example.msp_app.core.common.time

import com.example.msp_app.core.testing.time.FakeClock
import java.time.Duration
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppClockTest {

    @Test
    fun `System now returns a value close to real wall-clock time`() {
        val before = Instant.now()
        val result = AppClock.System.now()
        val after = Instant.now()

        assertFalse(
            "System.now() must not be before the surrounding real clock",
            result.isBefore(before)
        )
        assertFalse(
            "System.now() must not be after the surrounding real clock",
            result.isAfter(after)
        )
    }

    @Test
    fun `System now is unaffected by the JVM default TimeZone`() {
        // Instant carries no zone, so this is definitional, but it is exactly the property
        // AppClock/AppTime must preserve end-to-end — pin it explicitly at the clock boundary too.
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = AppClock.System.now()

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = AppClock.System.now()

            // Both calls happen within the same test; allow a small tolerance for wall-clock
            // drift between the two calls instead of asserting exact equality.
            assertTrue(Duration.between(underUtc, underTijuana).abs() < Duration.ofSeconds(5))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `a fake AppClock returns exactly the injected instant, not the host's now`() {
        val fixed = Instant.parse("2026-04-15T18:00:00Z")
        val clock: AppClock = FakeClock(fixed)

        assertEquals(fixed, clock.now())
        assertEquals(fixed, clock.now())
    }
}
