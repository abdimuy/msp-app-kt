package com.example.msp_app.features.payments.newpayment

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 6 introduced [currentPaymentTimestamp] as the seam that replaced the inline
 * `Instant.now().toString()` calls in `NewPaymentDialog` and `NewForgivenessDialog`.
 *
 * **Task 5b — DELIBERATE money-path behavior change (documented, not accidental):**
 * the helper now truncates to whole seconds
 * (`AppTime.toWireFormat(now.truncatedTo(SECONDS))`). Previously it emitted the
 * millisecond fraction that `Instant.now().toString()` produced (`.SSS`). The new width
 * matches, byte-for-byte:
 *  - what the payment upload already sends (`PaymentV2Mappers.normalizeFechaHoraPago`
 *    truncates to seconds — NOT because Go rejects fractions, `time.RFC3339` parses them
 *    fine, but to keep one stable second-precision width), and
 *  - what the server returns for confirmed pagos.
 *
 * With both local captures and server-normalized rows now at second precision, Room's
 * lexicographic string comparison at day boundaries is consistent, and (together with the
 * half-open DAO ranges in Task 5b) the business-midnight double-count is removed. The
 * server-visible value is unchanged (the upload already truncated); only the stored width
 * changes. Idempotency is keyed on `payment.ID` (a UUID), never on the timestamp.
 *
 * The old→new characterization lives in region 2 below.
 */
class PaymentTimestampTest {

    // region — 1. currentPaymentTimestamp truncates the clock's instant to whole seconds

    @Test
    fun `currentPaymentTimestamp emits the clock's instant as Z-UTC wire format (whole seconds unchanged)`() {
        val fixed = Instant.parse("2026-05-13T18:00:00Z")
        val clock = FakeClock(fixed)

        val result = currentPaymentTimestamp(clock)

        // No fraction to drop — identical to formatting the raw instant.
        assertEquals(AppTime.toWireFormat(fixed), result)
        assertEquals("2026-05-13T18:00:00Z", result)
    }

    @Test
    fun `currentPaymentTimestamp truncates a millisecond fraction to whole seconds`() {
        val fixed = Instant.parse("2026-05-13T18:05:23.142Z")
        val clock = FakeClock(fixed)

        val result = currentPaymentTimestamp(clock)

        assertEquals("2026-05-13T18:05:23Z", result)
        assertEquals(AppTime.toWireFormat(fixed.truncatedTo(ChronoUnit.SECONDS)), result)
    }

    @Test
    fun `currentPaymentTimestamp truncates a nanosecond fraction to whole seconds`() {
        val fixed = Instant.parse("2026-05-13T18:05:23.142789456Z")
        val clock = FakeClock(fixed)

        val result = currentPaymentTimestamp(clock)

        assertEquals("2026-05-13T18:05:23Z", result)
    }

    @Test
    fun `currentPaymentTimestamp reflects clock advancement, not a value captured at construction`() {
        val clock = FakeClock(Instant.parse("2026-05-13T18:00:00Z"))

        val before = currentPaymentTimestamp(clock)
        clock.advanceHours(2)
        val after = currentPaymentTimestamp(clock)

        assertEquals("2026-05-13T18:00:00Z", before)
        assertEquals("2026-05-13T20:00:00Z", after)
    }

    // endregion

    // region — 2. Characterization old→new: the fraction that USED to be written is now dropped
    //
    // OLD behavior (Task 6): currentPaymentTimestamp(clock) == clock.now().toString() —
    //   `Instant.toString()` keeps whatever sub-second fraction the instant carries.
    // NEW behavior (Task 5b): the fraction is truncated to whole seconds before formatting.
    // The matrix below pins that difference so a revert (dropping `truncatedTo(SECONDS)`)
    // fails loudly instead of silently re-widening the stored FECHA_HORA_PAGO.

    @Test
    fun `char-test old-to-new - a fractional instant now stores without its fraction`() {
        val fixed = Instant.parse("2026-05-13T18:05:23.142Z")
        val clock = FakeClock(fixed)

        val old = fixed.toString() // what Task 6 (Instant.now().toString()) would have stored
        val new = currentPaymentTimestamp(clock) // what Task 5b stores

        assertEquals("2026-05-13T18:05:23.142Z", old)
        assertEquals("2026-05-13T18:05:23Z", new)
        assertFalse("Task 5b must drop the sub-second fraction the old path kept", old == new)
        // Same instant to the second — the truncation removes only the sub-second part.
        assertEquals(
            AppTime.parseWireFormat(new),
            AppTime.parseWireFormat(old).truncatedTo(ChronoUnit.SECONDS)
        )
    }

    @Test
    fun `char-test old-to-new - a whole-second instant is unaffected (old == new)`() {
        val fixed = Instant.parse("2026-05-13T18:00:00Z")
        val clock = FakeClock(fixed)

        // With no fraction to drop, the new path is byte-identical to the old one.
        assertEquals(fixed.toString(), currentPaymentTimestamp(clock))
    }

    // endregion

    // region — 3. Shape msp-api accepts (RFC3339 `Z`-UTC, no fractional seconds).
    // The whole point of Task 5b's truncation is that the stored value is now ALWAYS the
    // no-fraction shape the server's `time.Parse(time.RFC3339, raw)` contract wants.

    @Test
    fun `output is always the no-fraction RFC3339 shape - whole-second input`() {
        val clock = FakeClock(Instant.parse("2026-05-13T18:00:00Z"))
        val result = currentPaymentTimestamp(clock)

        assertEquals("2026-05-13T18:00:00Z", result)
        assertFalse("no fractional seconds", result.contains('.'))
        assertEquals(Instant.parse("2026-05-13T18:00:00Z"), AppTime.parseWireFormat(result))
    }

    @Test
    fun `output is always the no-fraction RFC3339 shape - fractional input is truncated`() {
        val clock = FakeClock(Instant.parse("2026-05-13T18:05:23.142Z"))
        val result = currentPaymentTimestamp(clock)

        assertEquals("2026-05-13T18:05:23Z", result)
        assertFalse("no fractional seconds", result.contains('.'))
        assertEquals(Instant.parse("2026-05-13T18:05:23Z"), AppTime.parseWireFormat(result))
    }

    // endregion

    // region — 4. Default parameter uses AppClock.System (production wiring, sanity only)

    @Test
    fun `default clock parameter produces a well-formed no-fraction Z-UTC wire string`() {
        val result = currentPaymentTimestamp()

        // Real wall-clock time — assert only the shape and a clean round-trip.
        assertEquals(result, AppTime.toWireFormat(AppTime.parseWireFormat(result)))
        assertTrue(result.endsWith("Z"))
        assertFalse("truncated write path never emits fractional seconds", result.contains('.'))
    }

    // endregion
}
