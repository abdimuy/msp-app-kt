package com.example.msp_app.features.sales.components.paymentshistorysection

import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.models.payment.Payment
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD suite for Task 9 of the fechas/AppTime migration — [isFirstPaymentOfToday], extracted
 * from `PaymentsHistory` to fix a latent zone-consistency bug (see the KDoc on the function
 * under test): `datePayment` used to come from `DateUtils.formatIsoDate` (device zone) and
 * `dateNow` from a bare `LocalDate.now()` (also device zone) — self-consistent only by
 * accident. Migrating `datePayment` alone to business zone without also moving `dateNow`
 * would have broken the "is this the first payment of today" highlight for any cobrador whose
 * device zone differs from CDMX.
 */
class PaymentsHistorySectionTest {

    private fun payment(id: String, fechaHoraPago: String) = Payment(
        ID = id,
        COBRADOR = "Rosa Elena Martínez",
        DOCTO_CC_ACR_ID = 1001,
        DOCTO_CC_ID = 501,
        FECHA_HORA_PAGO = fechaHoraPago,
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 350.0,
        LAT = 19.4326,
        LNG = -99.1332,
        CLIENTE_ID = 24037,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 3,
        NOMBRE_CLIENTE = "Minerva López"
    )

    @Test
    fun `is true when payment is the first payment and was made today (business zone)`() {
        val clock = FakeClock.at("2026-04-15T15:00:00Z") // 09:00 CDMX
        val p = payment("pago-1", "2026-04-15T14:00:00Z") // 08:00 CDMX, same business day

        assertTrue(isFirstPaymentOfToday(p, firstPayment = p, clock = clock))
    }

    @Test
    fun `is false when the payment id does not match the group's first payment`() {
        val clock = FakeClock.at("2026-04-15T15:00:00Z")
        val p = payment("pago-2", "2026-04-15T14:00:00Z")
        val first = payment("pago-1", "2026-04-15T14:00:00Z")

        assertFalse(isFirstPaymentOfToday(p, firstPayment = first, clock = clock))
    }

    @Test
    fun `is false when the first payment is null (empty group)`() {
        val clock = FakeClock.at("2026-04-15T15:00:00Z")
        val p = payment("pago-1", "2026-04-15T14:00:00Z")

        assertFalse(isFirstPaymentOfToday(p, firstPayment = null, clock = clock))
    }

    @Test
    fun `is false when the payment was made on a previous business day`() {
        val clock = FakeClock.at("2026-04-15T15:00:00Z") // today = 2026-04-15 CDMX
        val p = payment("pago-1", "2026-04-14T14:00:00Z") // 2026-04-14 CDMX

        assertFalse(isFirstPaymentOfToday(p, firstPayment = p, clock = clock))
    }

    @Test
    fun `23-00 CDMX boundary - a late payment still counts as today despite the next-day UTC date`() {
        // Payment captured at 23:30 CDMX on 2026-04-15 -> 2026-04-16T05:30:00Z in UTC.
        // "Now" is later the same business day, still 2026-04-15 in device-agnostic terms
        // would actually already be 2026-04-16 by then — use a "now" a few minutes later,
        // same business day, to isolate the boundary from the payment's own instant.
        val payment = payment("pago-1", "2026-04-16T05:30:00Z") // 23:30 CDMX 2026-04-15
        val clock = FakeClock.at("2026-04-16T05:45:00Z") // 23:45 CDMX 2026-04-15 (still today)

        assertTrue(isFirstPaymentOfToday(payment, firstPayment = payment, clock = clock))
    }

    @Test
    fun `is independent of the device default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val clock = FakeClock.at("2026-04-16T05:45:00Z")
            val p = payment("pago-1", "2026-04-16T05:30:00Z")

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = isFirstPaymentOfToday(p, firstPayment = p, clock = clock)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = isFirstPaymentOfToday(p, firstPayment = p, clock = clock)

            assertEquals(underUtc, underTijuana)
            assertTrue(underUtc)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
