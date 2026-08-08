package com.example.msp_app.workers

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.time.LocalDateTime
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [isWithinWorkingHours] boundaries, plus a character test proving the gate now evaluates
 * in the business zone (`America/Mexico_City`) rather than the device zone — the fix for
 * bug #5: a cobrador with the phone set to another timezone used to activate/skip sync at
 * the wrong local business hour.
 */
class ClienteSyncWorkerWorkingHoursTest {

    @Test
    fun `06_59 is outside working hours`() {
        assertFalse(isWithinWorkingHours(LocalDateTime.of(2026, 4, 15, 6, 59)))
    }

    @Test
    fun `07_00 is within working hours`() {
        assertTrue(isWithinWorkingHours(LocalDateTime.of(2026, 4, 15, 7, 0)))
    }

    @Test
    fun `12_00 is within working hours`() {
        assertTrue(isWithinWorkingHours(LocalDateTime.of(2026, 4, 15, 12, 0)))
    }

    @Test
    fun `17_59 is within working hours`() {
        assertTrue(isWithinWorkingHours(LocalDateTime.of(2026, 4, 15, 17, 59)))
    }

    @Test
    fun `18_00 is outside working hours`() {
        assertFalse(isWithinWorkingHours(LocalDateTime.of(2026, 4, 15, 18, 0)))
    }

    @Test
    fun `23_00 is outside working hours`() {
        assertFalse(isWithinWorkingHours(LocalDateTime.of(2026, 4, 15, 23, 0)))
    }

    @Test
    fun `00_00 is outside working hours`() {
        assertFalse(isWithinWorkingHours(LocalDateTime.of(2026, 4, 15, 0, 0)))
    }

    @Test
    fun `gate evaluates business zone hour, not device zone hour`() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            // Device set to UTC: same instant reads as a different hour than in CDMX.
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

            // 23:30 UTC == 17:30 CDMX (America/Mexico_City is fixed UTC-6, no DST since 2022).
            val instant = Instant.parse("2026-04-15T23:30:00Z")
            val fakeClock = FakeClock(instant)

            val deviceHour = Calendar.getInstance().apply { timeInMillis = instant.toEpochMilli() }
                .get(Calendar.HOUR_OF_DAY)
            assertEquals(23, deviceHour)
            // Old behavior (device zone, pre-fix): 23:30 is outside working hours.
            assertFalse(isWithinWorkingHours(LocalDateTime.of(2026, 4, 15, deviceHour, 30)))

            val businessNow = AppTime.nowInBusinessZone(fakeClock)
            assertEquals(17, businessNow.hour)
            // New behavior (business zone, post-fix): 17:30 CDMX is within working hours.
            assertTrue(isWithinWorkingHours(businessNow))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }
}
