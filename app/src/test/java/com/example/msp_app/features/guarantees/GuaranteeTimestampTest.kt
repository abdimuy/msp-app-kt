package com.example.msp_app.features.guarantees

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 11 (fechas/AppTime migration) — [currentGuaranteeTimestamp] is the testable seam
 * that replaced `DateTimeFormatter.ISO_INSTANT.format(Instant.now())` at the two guarantee
 * call sites that could not own an injected `AppClock` directly:
 *  - `GuaranteesScreen.GuaranteeScreen` (bare `@Composable`, no ViewModel).
 *  - `CreateGuaranteeViewModel.saveGuarantee` (`AndroidViewModel`, not unit-tested directly
 *    in this codebase — see `NewTransferViewModel`'s clock-as-property pattern).
 *
 * The wire-format BUG (#4) itself lived in `GuaranteesLocalDataSource` (`FECHA_EVENTO`,
 * `LocalDateTime.now().format(ISO_LOCAL_DATE_TIME)` — no zone/offset) and is covered
 * separately in `GuaranteesLocalDataSourceFechaEventoTest`.
 */
class GuaranteeTimestampTest {

    // region — 1. Z-UTC wire format from the injected clock

    @Test
    fun `currentGuaranteeTimestamp emits the clock's instant as Z-UTC wire format`() {
        val fixed = Instant.parse("2026-08-08T14:30:00Z")
        val clock = FakeClock(fixed)

        val result = currentGuaranteeTimestamp(clock)

        assertEquals(AppTime.toWireFormat(fixed), result)
        assertEquals("2026-08-08T14:30:00Z", result)
        assertTrue(result.endsWith("Z"))
    }

    @Test
    fun `currentGuaranteeTimestamp reflects clock advancement, not a value captured at construction`() {
        val clock = FakeClock(Instant.parse("2026-08-08T14:30:00Z"))

        val before = currentGuaranteeTimestamp(clock)
        clock.advanceHours(3)
        val after = currentGuaranteeTimestamp(clock)

        assertEquals("2026-08-08T14:30:00Z", before)
        assertEquals("2026-08-08T17:30:00Z", after)
    }

    // endregion

    // region — 2. Mutation-kill: a revert to ISO_LOCAL_DATE_TIME (no zone) must fail this test

    @Test
    fun `output is NOT the offset-less ISO_LOCAL_DATE_TIME shape the old bug produced`() {
        val fixed = Instant.parse("2026-08-08T14:30:00Z")
        val clock = FakeClock(fixed)

        val result = currentGuaranteeTimestamp(clock)

        // The old (buggy) shape for this instant under CDMX wall-clock would have been
        // "2026-08-08T08:30:00" (no Z, no offset) — LocalDateTime.now() ignores the
        // injected clock entirely and reads a completely different (real) wall-clock time,
        // so if a mutation reintroduced that path, this string comparison would fail.
        val oldBuggyShape = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        assertFalse(result == oldBuggyShape)
        assertFalse(
            "must not contain the offset-less LocalDateTime shape",
            result.contains(oldBuggyShape)
        )
        assertTrue("wire format must end with Z", result.endsWith("Z"))
    }

    // endregion

    // region — 3. Device-zone independence

    @Test
    fun `currentGuaranteeTimestamp is independent of the device default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val clock = FakeClock(Instant.parse("2026-08-08T14:30:00Z"))

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = currentGuaranteeTimestamp(clock)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = currentGuaranteeTimestamp(clock)

            assertEquals(underUtc, underTijuana)
            assertEquals("2026-08-08T14:30:00Z", underUtc)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // endregion

    // region — 4. Default parameter uses AppClock.System (production wiring, sanity only)

    @Test
    fun `default clock parameter produces a well-formed Z-UTC wire string`() {
        val result = currentGuaranteeTimestamp()

        assertEquals(result, AppTime.toWireFormat(AppTime.parseWireFormat(result)))
        assertTrue(result.endsWith("Z"))
    }

    // endregion
}
