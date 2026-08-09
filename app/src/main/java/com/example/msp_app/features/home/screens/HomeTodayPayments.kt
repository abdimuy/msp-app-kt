package com.example.msp_app.features.home.screens

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.payment.Payment
import java.time.LocalDate

/**
 * Pure lookup of "[day]'s payments" out of the day-grouped map exposed by
 * [com.example.msp_app.features.payments.viewmodels.PaymentsViewModel.paymentsGroupedByDayWeeklyState].
 *
 * The map's keys are calendar-day strings (`yyyy-MM-dd`) computed in BUSINESS zone
 * (`America/Mexico_City`) by `PaymentDao`'s `dayKeyOf` (Task 3 of the fechas/AppTime
 * migration, `date-lib-audit.md` bug #1/#7). [day] MUST therefore be obtained the same way —
 * [AppTime.todayInBusinessZone] — for the lookup to find "today's" payments.
 *
 * **Money-ish bug this closes (Task 3 debt, Task 12b):** the call site used to pass
 * `LocalDate.now()` (the DEVICE's zone) here instead of the business-zone date. On a phone set
 * to a different timezone near midnight, the device-zone key mismatches the map's
 * business-zone key and "pagos de hoy" (total + count) silently reads 0 / empty even though
 * payments exist for the actual business day. Characterized old-vs-new in
 * `HomeTodayPaymentsTest`.
 */
internal fun paymentsGroupedByDay(
    state: ResultState<Map<String, List<Payment>>>,
    day: LocalDate
): List<Payment> {
    val map = (state as? ResultState.Success<Map<String, List<Payment>>>)?.data
    return map?.get(day.toString()) ?: emptyList()
}
