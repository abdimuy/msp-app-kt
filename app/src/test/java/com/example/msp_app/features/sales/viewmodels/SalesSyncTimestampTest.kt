package com.example.msp_app.features.sales.viewmodels

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 12 (fechas/AppTime migration, bug #8) — write side of the "última sincronización"
 * round-trip. Paired with [com.example.msp_app.features.home.components.homestartweeksection.LastSyncDisplayTest]
 * (read side) for the full round-trip proof.
 */
class SalesSyncTimestampTest {

    @Test
    fun `currentSalesLastSync emits the clock's instant as Z-UTC wire format`() {
        val fixed = Instant.parse("2026-08-08T14:30:00Z")
        val clock = FakeClock(fixed)

        val result = currentSalesLastSync(clock)

        assertEquals(AppTime.toWireFormat(fixed), result)
        assertEquals("2026-08-08T14:30:00Z", result)
        assertTrue(result.endsWith("Z"))
    }

    @Test
    fun `currentSalesLastSync reflects clock advancement, not a value captured at construction`() {
        val clock = FakeClock(Instant.parse("2026-08-08T14:30:00Z"))

        val before = currentSalesLastSync(clock)
        clock.advanceHours(2)
        val after = currentSalesLastSync(clock)

        assertEquals("2026-08-08T14:30:00Z", before)
        assertEquals("2026-08-08T16:30:00Z", after)
    }

    @Test
    fun `currentSalesLastSync is independent of the device default Locale`() {
        val original = Locale.getDefault()
        try {
            val clock = FakeClock(Instant.parse("2026-08-08T14:30:00Z"))

            Locale.setDefault(Locale.US)
            val underUs = currentSalesLastSync(clock)

            Locale.setDefault(Locale("ar"))
            val underAr = currentSalesLastSync(clock)

            Locale.setDefault(Locale("es", "MX"))
            val underEsMx = currentSalesLastSync(clock)

            assertEquals("2026-08-08T14:30:00Z", underUs)
            assertEquals(underUs, underAr)
            assertEquals(underUs, underEsMx)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `default clock parameter produces a well-formed Z-UTC wire string`() {
        val result = currentSalesLastSync()

        assertEquals(result, AppTime.toWireFormat(AppTime.parseWireFormat(result)))
        assertTrue(result.endsWith("Z"))
    }
}
