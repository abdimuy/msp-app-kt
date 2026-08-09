package com.example.msp_app.features.sales.components.paymentcard

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 12b (fechas/AppTime migration) — background-gradient decision in `PaymentCard.kt`,
 * migrated off `DateUtils.isAfterIso` (naive `LocalDateTime` comparison) to
 * [isPaymentAfterInitialLoad] (direct `Instant` comparison via `AppTime.parseWireFormat`).
 *
 * See [isPaymentAfterInitialLoad]'s KDoc for the full audit: for the only shape production ever
 * writes (`Z`-suffixed UTC strings from `AppTime.toWireFormat` / the old `DateUtils
 * .getIsoDateTime`), the old naive-`LocalDateTime` comparison and the new `Instant` comparison
 * are ORDER-EQUIVALENT — neither reads `ZoneId.systemDefault()` / the device's default zone, so
 * there is no "device-zone bug" for that realistic shape (regions 1-2 below characterize this
 * equivalence, including under different JVM default `TimeZone`s). Region 3 documents the one
 * shape where old and new genuinely diverge: a legacy zone-less ("naive") `T` string mixed with
 * a proper `Z` string — out of practical reach given production always writes `Z`-suffixed
 * values, but characterized for completeness per the Task 12b brief.
 */
class PaymentCardBackgroundTest {

    // region — 1. Realistic Z-suffixed inputs: success/regular decision, strict "after"

    @Test
    fun `payment after dateInitial shows the success background`() {
        val result = isPaymentAfterInitialLoad(
            paymentDateIso = "2026-04-16T10:00:00Z",
            dateInitialIso = "2026-04-16T08:00:00Z"
        )
        assertTrue(result)
    }

    @Test
    fun `payment before dateInitial shows the regular background`() {
        val result = isPaymentAfterInitialLoad(
            paymentDateIso = "2026-04-16T06:00:00Z",
            dateInitialIso = "2026-04-16T08:00:00Z"
        )
        assertFalse(result)
    }

    @Test
    fun `payment exactly at dateInitial is NOT after it (strict comparison, preserved from old isAfterIso)`() {
        val result = isPaymentAfterInitialLoad(
            paymentDateIso = "2026-04-16T08:00:00Z",
            dateInitialIso = "2026-04-16T08:00:00Z"
        )
        assertFalse(result)
    }

    // endregion

    // region — 2. Device-zone independence (both old and new: neither reads systemDefault here,
    // but this is asserted explicitly as a regression guard for the migration)

    @Test
    fun `decision is independent of the JVM default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val paymentIso = "2026-04-16T10:00:00Z"
            val dateInitialIso = "2026-04-16T08:00:00Z"

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = isPaymentAfterInitialLoad(paymentIso, dateInitialIso)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = isPaymentAfterInitialLoad(paymentIso, dateInitialIso)

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val underKiritimati = isPaymentAfterInitialLoad(paymentIso, dateInitialIso)

            assertTrue(underUtc)
            assertEquals(underUtc, underTijuana)
            assertEquals(underUtc, underKiritimati)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // endregion

    // region — 3. Documented divergence: legacy zone-less ("naive") T-string on one side vs. a
    // proper Z string on the other. NOT a device-zone scenario (no ZoneId.systemDefault() is
    // read by either the old or the new code) — it is old-naive-digits vs.
    // new-business-zone-anchored interpretation of the SAME zone-less legacy shape, per
    // isPaymentAfterInitialLoad's KDoc. Out of practical reach in production, where both values
    // are always Z-suffixed by construction.

    @Test
    fun `OLD naive comparison and NEW instant comparison diverge for a legacy zone-less dateInitial mixed with a Z payment`() {
        // dateInitial: legacy shape, no offset/Z — OLD takes "00:15" literally as naive
        // wall-clock digits; NEW (AppTime.parseWireFormat) anchors it to BUSINESS_ZONE
        // (America/Mexico_City, UTC-6), i.e. treats it as 2026-04-16T06:15:00Z.
        val dateInitialIso = "2026-04-16T00:15:00"
        // payment: proper Z string, digitally "after" 00:15 but chronologically BEFORE the
        // true (business-zone) instant of dateInitial.
        val paymentIso = "2026-04-16T00:30:00Z"

        // OLD behaviour, reproduced verbatim from the removed `DateUtils.parseIsoToDateTime`:
        // both sides compared as naive LocalDateTime, dateInitial's digits taken as-is.
        val oldPaymentNaive = OffsetDateTime.parse(paymentIso).toLocalDateTime()
        val oldDateInitialNaive = LocalDateTime.parse(dateInitialIso)
        val oldResult = oldPaymentNaive.isAfter(oldDateInitialNaive)
        assertTrue("OLD (naive digit compare) must say 'after': got $oldResult", oldResult)

        // NEW behaviour: real Instant comparison, dateInitial's naive digits anchored to
        // BUSINESS_ZONE before comparing.
        val newResult = isPaymentAfterInitialLoad(paymentIso, dateInitialIso)
        assertFalse(
            "NEW (business-zone-anchored instant compare) must say 'NOT after': got $newResult",
            newResult
        )

        assertTrue(
            "this test only demonstrates the fix if OLD and NEW actually disagree",
            oldResult != newResult
        )
    }

    // endregion
}
