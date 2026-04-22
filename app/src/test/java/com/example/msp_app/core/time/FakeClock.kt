package com.example.msp_app.core.time

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Test double for [AppClock]. Construct with a fixed instant; advance as needed.
 *
 * ```kotlin
 * val clock = FakeClock.at("2026-04-15T23:30:00-06:00") // 23:30 CDMX
 * val today = AppTime.todayInBusinessZone(clock) // 2026-04-15
 * clock.advanceHours(1)
 * // now UTC day rolled; business date is still 2026-04-16 etc.
 * ```
 */
class FakeClock(initial: Instant = DEFAULT_INITIAL) : AppClock {

    private var current: Instant = initial

    override fun now(): Instant = current

    fun setNow(instant: Instant) {
        current = instant
    }

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    fun advanceSeconds(seconds: Long) = advance(Duration.ofSeconds(seconds))
    fun advanceMinutes(minutes: Long) = advance(Duration.ofMinutes(minutes))
    fun advanceHours(hours: Long) = advance(Duration.ofHours(hours))
    fun advanceDays(days: Long) {
        current = current.plus(days, ChronoUnit.DAYS)
    }

    companion object {
        private val DEFAULT_INITIAL: Instant = Instant.parse("2026-04-15T18:00:00Z")

        /** Convenience: build from any ISO string [AppTime.parseWireFormat] accepts. */
        fun at(iso: String): FakeClock = FakeClock(AppTime.parseWireFormat(iso))
    }
}
