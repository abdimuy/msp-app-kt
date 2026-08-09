package com.example.msp_app.features.sales.components.paymentcard

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
 * `dateInitial` in `PaymentCard.kt` —
 * ```kotlin
 * val dateInitial = user?.FECHA_CARGA_INICIAL?.toDate()?.toInstant()
 *     ?.let { AppTime.toWireFormat(it) }
 *     ?: AppTime.toWireFormat(AppClock.System.now())
 * ```
 * — is NOT behavior-neutral versus the removed `DateUtils.getIsoDateTime()` (no-arg) fallback.
 * It is a benign FAVORABLE FIX, pinned here instead of only asserted in prose. See
 * `HomeStartWeekDateFallbackTest` in `features.home.screens` for the identical-shape fallback in
 * `Home.kt` — same underlying bug, same fix, duplicated here because the two call sites are
 * independent production seams the reviewers flagged separately.
 *
 * `DateUtils.getIsoDateTime()`'s body labels the DEVICE's naive wall-clock digits
 * (`LocalDateTime.now()`) as UTC (`.atZone(ZoneOffset.UTC)`) instead of converting them — wrong
 * by the device's UTC offset whenever that offset isn't zero. `AppTime.toWireFormat
 * (AppClock.System.now())` uses the TRUE current instant, always correct regardless of device
 * zone. Scope: only the narrow window before `authViewModel.userData` has loaded (`user ==
 * null`); with data loaded, `isPaymentAfterInitialLoad` compares against the real
 * `FECHA_CARGA_INICIAL` instead.
 */
class PaymentCardDateInitialFallbackTest {

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

            // OLD code, inlined verbatim from the removed `DateUtils.getIsoDateTime()`.
            // `LocalDateTime.now()` is simulated deterministically for the SAME real instant
            // via `Clock.fixed(fixedInstant, ZoneId.systemDefault())`.
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
