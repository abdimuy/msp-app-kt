package com.example.msp_app.core.logging

import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 12 (fechas/AppTime migration, bug #9) — [remoteLoggerTimestampString] is the testable
 * seam extracted from `RemoteLogger.log`'s
 * `SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())`. [RemoteLogger]
 * itself is not unit-tested directly in this codebase (Firebase Firestore/Auth dependencies,
 * no fakes available — fakes-only test policy); the authoritative `timestamp` field stored
 * alongside this human-readable duplicate remains Firebase's `Timestamp.now()`, unaffected by
 * this change.
 */
class RemoteLoggerTimestampTest {

    @Test
    fun `formats as yyyy-MM-dd HH-mm-ss in business zone`() {
        val fixed = Instant.parse("2026-08-08T14:30:05Z") // 08:30:05 CDMX
        val clock = FakeClock(fixed)

        val result = remoteLoggerTimestampString(clock)

        assertEquals("2026-08-08 08:30:05", result)
    }

    @Test
    fun `is independent of the device default Locale`() {
        val original = Locale.getDefault()
        try {
            val clock = FakeClock(Instant.parse("2026-08-08T14:30:05Z"))

            Locale.setDefault(Locale.US)
            val underUs = remoteLoggerTimestampString(clock)

            Locale.setDefault(Locale("ar"))
            val underAr = remoteLoggerTimestampString(clock)

            assertEquals(underUs, underAr)
            assertEquals("2026-08-08 08:30:05", underUs)
        } finally {
            Locale.setDefault(original)
        }
    }
}
