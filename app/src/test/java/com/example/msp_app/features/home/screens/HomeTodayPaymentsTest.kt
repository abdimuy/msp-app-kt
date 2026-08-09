package com.example.msp_app.features.home.screens

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.payment.Payment
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 12b (fechas/AppTime migration) — day-grouping lookup for Home's "pagos de hoy" (total +
 * count), Task 3 debt. `Home.kt` used to look the current day up in
 * `paymentsGroupedByDayWeekly` (a map keyed by BUSINESS-zone day, see `PaymentDao.dayKeyOf`)
 * via `LocalDate.now()` — the DEVICE's zone. Near midnight on a phone set to another timezone,
 * the device-zone key does not match the map's business-zone key and the lookup silently
 * misses today's payments.
 *
 * [paymentsGroupedByDay] is zone-agnostic by construction — it just takes an explicit [LocalDate]
 * key — so the "old vs new" divergence is demonstrated by feeding it two DIFFERENT `day`
 * arguments computed for the SAME instant: the OLD device-zone date (simulated via
 * `LocalDate.now(Clock.fixed(instant, deviceZone))`, i.e. what the removed
 * `LocalDate.now()` call site would have evaluated to) and the NEW business-zone date
 * ([AppTime.todayInBusinessZone]).
 */
class HomeTodayPaymentsTest {

    // Fixed instant chosen so business-zone (America/Mexico_City, UTC-6, no DST since 2022)
    // and device-zone (Pacific/Kiritimati, UTC+14, no DST) land on DIFFERENT calendar days:
    //   business: 2026-04-16T05:30:00Z - 6h = 2026-04-15 23:30 -> LocalDate 2026-04-15
    //   device:   2026-04-16T05:30:00Z + 14h = 2026-04-16 19:30 -> LocalDate 2026-04-16
    private val fixedInstant: Instant = Instant.parse("2026-04-16T05:30:00Z")
    private val deviceZone: ZoneId = ZoneId.of("Pacific/Kiritimati")

    private fun payment(id: String) = Payment(
        ID = id,
        COBRADOR = "Rosa Elena Martínez",
        DOCTO_CC_ACR_ID = 1001,
        DOCTO_CC_ID = 501,
        FECHA_HORA_PAGO = "2026-04-15T23:45:00Z",
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
    fun `business-zone and device-zone dates diverge for the fixture instant (sanity check)`() {
        val businessToday = AppTime.todayInBusinessZone(FakeClock(fixedInstant))
        val deviceToday = LocalDate.now(Clock.fixed(fixedInstant, deviceZone))

        assertEquals(LocalDate.of(2026, 4, 15), businessToday)
        assertEquals(LocalDate.of(2026, 4, 16), deviceToday)
        assertNotEquals(businessToday, deviceToday)
    }

    @Test
    fun `NEW business-zone lookup finds todays payments that the OLD device-zone key would miss`() {
        val businessToday = AppTime.todayInBusinessZone(FakeClock(fixedInstant))
        val todaysPayment = payment("pago-1")
        val state: ResultState<Map<String, List<Payment>>> =
            ResultState.Success(mapOf(businessToday.toString() to listOf(todaysPayment)))

        // NEW: lookup keyed by AppTime.todayInBusinessZone() — finds the payment.
        val newResult = paymentsGroupedByDay(state, businessToday)
        assertEquals(listOf(todaysPayment), newResult)

        // OLD: lookup keyed by the removed call site's LocalDate.now() (device zone) —
        // simulated deterministically via Clock.fixed for the same real instant.
        val deviceToday = LocalDate.now(Clock.fixed(fixedInstant, deviceZone))
        val oldResult = paymentsGroupedByDay(state, deviceToday)
        assertTrue("OLD device-zone key must MISS: got $oldResult", oldResult.isEmpty())
    }

    @Test
    fun `lookup is independent of the JVM default TimeZone`() {
        val original = TimeZone.getDefault()
        try {
            val businessToday = AppTime.todayInBusinessZone(FakeClock(fixedInstant))
            val todaysPayment = payment("pago-2")
            val state: ResultState<Map<String, List<Payment>>> =
                ResultState.Success(mapOf(businessToday.toString() to listOf(todaysPayment)))

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = paymentsGroupedByDay(state, businessToday)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = paymentsGroupedByDay(state, businessToday)

            assertEquals(listOf(todaysPayment), underUtc)
            assertEquals(listOf(todaysPayment), underTijuana)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `missing day returns empty list, not a crash`() {
        val state: ResultState<Map<String, List<Payment>>> =
            ResultState.Success(mapOf("2026-04-10" to listOf(payment("pago-3"))))

        val result = paymentsGroupedByDay(state, LocalDate.of(2026, 4, 15))

        assertEquals(emptyList<Payment>(), result)
    }

    @Test
    fun `non-Success state returns empty list`() {
        assertEquals(
            emptyList<Payment>(),
            paymentsGroupedByDay(ResultState.Loading, LocalDate.of(2026, 4, 15))
        )
        assertEquals(
            emptyList<Payment>(),
            paymentsGroupedByDay(ResultState.Error("boom"), LocalDate.of(2026, 4, 15))
        )
        assertEquals(
            emptyList<Payment>(),
            paymentsGroupedByDay(ResultState.Idle, LocalDate.of(2026, 4, 15))
        )
    }
}
