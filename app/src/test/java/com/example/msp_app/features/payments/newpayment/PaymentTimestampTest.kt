package com.example.msp_app.features.payments.newpayment

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 6 — [currentPaymentTimestamp] is the seam that replaced the inline
 * `Instant.now().toString()` calls in `NewPaymentDialog` and `NewForgivenessDialog`
 * (bug #10 of the date-lib audit: not testable, silent-regression risk).
 *
 * This is a MONEY-PATH refactor with an explicit no-behavior-change constraint: the string
 * written to `FECHA_HORA_PAGO` must stay byte-identical to what `Instant.now().toString()`
 * used to produce. Test (2) below is the characterization proof of that equivalence — it is
 * the test that would fail if a future edit swapped [AppTime.toWireFormat] for a formatter
 * that behaves differently from `Instant.toString()` (e.g. truncates fractional seconds).
 */
class PaymentTimestampTest {

    // region — 1. currentPaymentTimestamp(fake) == AppTime.toWireFormat(fixedInstant), exactly

    @Test
    fun `currentPaymentTimestamp uses the clock's instant, formatted as Z-UTC wire format (whole seconds)`() {
        val fixed = Instant.parse("2026-05-13T18:00:00Z")
        val clock = FakeClock(fixed)

        val result = currentPaymentTimestamp(clock)

        assertEquals(AppTime.toWireFormat(fixed), result)
        assertEquals("2026-05-13T18:00:00Z", result)
    }

    @Test
    fun `currentPaymentTimestamp uses the clock's instant, formatted as Z-UTC wire format (millis fraction)`() {
        val fixed = Instant.parse("2026-05-13T18:05:23.142Z")
        val clock = FakeClock(fixed)

        val result = currentPaymentTimestamp(clock)

        assertEquals(AppTime.toWireFormat(fixed), result)
        assertEquals("2026-05-13T18:05:23.142Z", result)
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

    // region — 2. Characterization: AppTime.toWireFormat(i) == i.toString() for all instants
    //
    // This is the behavior-neutral proof required by the brief: the old call sites did
    // `Instant.now().toString()`; the new seam does `AppTime.toWireFormat(clock.now())`. Both
    // format via DateTimeFormatter.ISO_INSTANT under the hood, so for ANY instant the two must
    // agree exactly. If this ever stops holding, the refactor silently changed the wire value
    // sent to msp-api — a money-path regression.

    @Test
    fun `AppTime toWireFormat matches Instant toString exactly - whole seconds`() {
        val i = Instant.parse("2026-05-13T18:00:00Z")
        assertEquals(i.toString(), AppTime.toWireFormat(i))
    }

    @Test
    fun `AppTime toWireFormat matches Instant toString exactly - millis fraction`() {
        val i = Instant.parse("2026-05-13T18:05:23.142Z")
        assertEquals(i.toString(), AppTime.toWireFormat(i))
    }

    @Test
    fun `AppTime toWireFormat matches Instant toString exactly - nanos fraction`() {
        val i = Instant.parse("2026-05-13T18:05:23.142789456Z")
        assertEquals(i.toString(), AppTime.toWireFormat(i))
    }

    @Test
    fun `AppTime toWireFormat matches Instant toString exactly - epoch`() {
        val i = Instant.EPOCH
        assertEquals(i.toString(), AppTime.toWireFormat(i))
    }

    @Test
    fun `AppTime toWireFormat matches Instant toString exactly - year boundary`() {
        val i = Instant.parse("2025-12-31T23:59:59.999Z")
        assertEquals(i.toString(), AppTime.toWireFormat(i))
    }

    @Test
    fun `currentPaymentTimestamp itself is proven identical to the old Instant now toString shape via the clock's instant`() {
        // Not "now()" (non-deterministic) — pins that currentPaymentTimestamp(clock) for a given
        // instant is exactly what `instant.toString()` (the old inline call's behavior) produces,
        // closing the loop between the characterization above and the actual seam under test.
        val fixed = Instant.parse("2026-05-13T18:05:23.142Z")
        val clock = FakeClock(fixed)

        assertEquals(fixed.toString(), currentPaymentTimestamp(clock))
    }

    // endregion

    // region — 3. Shape msp-api accepts (RFC3339 `Z`-UTC) — reuses the contract fixed by
    // WireContractTest (core/common) / Task 2: `time.Parse(time.RFC3339, raw)` accepts both the
    // whole-second and fractional-second forms below.

    @Test
    fun `currentPaymentTimestamp output matches the FechaHoraPago shape emitted and accepted by msp-api (no fraction)`() {
        val clock = FakeClock(Instant.parse("2026-05-13T18:00:00Z"))
        val result = currentPaymentTimestamp(clock)

        assertEquals("2026-05-13T18:00:00Z", result)
        // Round-trips cleanly through AppTime.parseWireFormat, the same parser msp-api's
        // `time.Parse(time.RFC3339, raw)` contract is pinned against in WireContractTest.
        assertEquals(Instant.parse("2026-05-13T18:00:00Z"), AppTime.parseWireFormat(result))
    }

    @Test
    fun `currentPaymentTimestamp output matches the RFC3339 shape with fractional seconds`() {
        val clock = FakeClock(Instant.parse("2026-05-13T18:05:23.142Z"))
        val result = currentPaymentTimestamp(clock)

        assertEquals("2026-05-13T18:05:23.142Z", result)
        assertEquals(Instant.parse("2026-05-13T18:05:23.142Z"), AppTime.parseWireFormat(result))
    }

    // endregion

    // region — 4. Default parameter uses AppClock.System (production wiring, sanity check only)

    @Test
    fun `default clock parameter produces a well-formed Z-UTC wire string`() {
        val result = currentPaymentTimestamp()

        // Not asserting a fixed value (this is real wall-clock time) — only the shape, and
        // that it round-trips through AppTime's own parser.
        assertEquals(result, AppTime.toWireFormat(AppTime.parseWireFormat(result)))
        assertEquals(true, result.endsWith("Z"))
    }

    // endregion
}
