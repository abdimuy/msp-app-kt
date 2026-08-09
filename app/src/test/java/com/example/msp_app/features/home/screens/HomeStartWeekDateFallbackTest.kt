package com.example.msp_app.features.home.screens

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Fix round 1/5 finding (shared by both money reviewers): the `user == null` fallback for
 * `startWeekDate` in `Home.kt:136-139` —
 * ```kotlin
 * val startWeekDate = remember(initialDate) {
 *     val startInstant = initialDate?.toDate()?.toInstant() ?: AppClock.System.now()
 *     AppTime.toWireFormat(startInstant)
 * }
 * ```
 * — is NOT behavior-neutral versus the removed `DateUtils.parseDateToIso(null)` fallback. It is
 * a benign FAVORABLE FIX, and this test pins that claim instead of just asserting it in prose.
 *
 * `DateUtils.parseDateToIso(null)` called `DateUtils.getIsoDateTime()` (no-arg), whose body is:
 * ```kotlin
 * val zoned = (dateTime ?: LocalDateTime.now()).atZone(java.time.ZoneOffset.UTC)
 * DateTimeFormatter.ISO_INSTANT.format(zoned)
 * ```
 * `LocalDateTime.now()` reads the DEVICE's wall-clock digits in the DEVICE's default zone, with
 * no zone attached to the value. `.atZone(ZoneOffset.UTC)` then LABELS those naive digits as
 * UTC — it does not convert them. On any device whose default zone isn't UTC, this produces a
 * WRONG instant, off by exactly the device's UTC offset.
 *
 * The new code (`AppTime.toWireFormat(AppClock.System.now())`) uses the TRUE current instant
 * regardless of device zone — it is always correct. Scope of the change: only the narrow window
 * before `userDataState` has loaded (`initialDate == null`); once the user record loads, this
 * branch is never taken again for that composition.
 */
class HomeStartWeekDateFallbackTest {

    // Real instant the "device" clock reads at test time.
    private val fixedInstant: Instant = Instant.parse("2026-08-08T14:30:00Z")

    @Test
    fun `NEW fallback yields the true current instant, independent of device zone`() {
        val clock = FakeClock(fixedInstant)

        val newResult = AppTime.toWireFormat(clock.now())

        assertEquals("2026-08-08T14:30:00Z", newResult)
    }

    @Test
    fun `NEW fallback is unaffected by the JVM default TimeZone (unlike the OLD one)`() {
        val original = TimeZone.getDefault()
        try {
            val clock = FakeClock(fixedInstant)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City")) // UTC-6, no DST
            val underCdmx = AppTime.toWireFormat(clock.now())

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")) // UTC+14, no DST
            val underKiritimati = AppTime.toWireFormat(clock.now())

            assertEquals("2026-08-08T14:30:00Z", underCdmx)
            assertEquals("2026-08-08T14:30:00Z", underKiritimati)
            assertEquals(underCdmx, underKiritimati)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `OLD DateUtils-getIsoDateTime fallback mislabels device wall-clock as UTC and diverges from the true instant`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City")) // UTC-6, no DST

            // OLD code, inlined verbatim from the removed `DateUtils.getIsoDateTime()`:
            // `(dateTime ?: LocalDateTime.now()).atZone(ZoneOffset.UTC)`. `LocalDateTime.now()`
            // is simulated deterministically for the SAME real instant via
            // `Clock.fixed(fixedInstant, ZoneId.systemDefault())` — this reproduces exactly
            // what the zero-arg call would have read at that moment under the zone just set.
            val oldDeviceWallClock = LocalDateTime.now(
                Clock.fixed(fixedInstant, ZoneId.systemDefault())
            )
            val oldResult = DateTimeFormatter.ISO_INSTANT.format(
                oldDeviceWallClock.atZone(ZoneOffset.UTC)
            )

            // 14:30 UTC - 6h = 08:30 CDMX wall clock, mislabeled as 08:30 UTC by the old code.
            assertEquals("2026-08-08T08:30:00Z", oldResult)

            val newResult = AppTime.toWireFormat(FakeClock(fixedInstant).now())
            assertEquals("2026-08-08T14:30:00Z", newResult)

            assertNotEquals(
                "OLD (mislabeled device wall-clock) and NEW (true instant) must differ under a " +
                    "non-UTC device zone — that IS the favorable fix",
                oldResult,
                newResult
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
