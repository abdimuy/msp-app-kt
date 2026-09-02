package com.example.msp_app.features.payments.utils

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import java.time.LocalDate

/**
 * Half-open range covering all of [startIso, endIso) — [startIso] is midnight of the report
 * date in business zone, [endIso] is midnight of the NEXT day (exclusive). See
 * [ReportFormatters.dateRangeFor].
 */
data class ReportDateRange(val startIso: String, val endIso: String)

object ReportFormatters {

    /**
     * Half-open one-day range `[startOfDay(date), startOfNextDay(date))` in business zone,
     * matching `msp-api`'s `[desde, hasta)` semantics byte-for-byte (see
     * `AppTime.startOfDay`/`AppTime.startOfNextDay` kdoc and
     * `msp-api/docs/module-standards/DATETIME_HANDLING.md`).
     *
     * Replaces the legacy date util's `parseLocalDateToIso(date)` (start, computed in the
     * DEVICE zone via `ZoneId.systemDefault()`) + `addToIsoDate(addToIsoDate(iso, 1,
     * DAYS), -1, SECONDS)` (end, "+1 day -1 second"). That legacy pattern had two independent
     * bugs (`date-lib-audit.md`): (1) the day boundary was anchored to the device's timezone,
     * not the business zone — a cobrador with a misconfigured/roaming phone could see the wrong
     * payments in a "daily" report; (2) `addToIsoDate` round-trips through `LocalDateTime`,
     * dropping the UTC offset carried by the input string and silently re-interpreting the
     * result as UTC (bug #3) — the "-1 second" end (`23:59:59`) could land at the wrong instant
     * by exactly the device's UTC offset, and always missed the last second/millisecond of the
     * day regardless.
     *
     * Pure and side-effect free — this is the function under test in
     * `ReportFormattersDateRangeTest` (boundary, device-zone independence, old-vs-new
     * characterization).
     */
    fun dateRangeFor(date: LocalDate): ReportDateRange = ReportDateRange(
        startIso = AppTime.toWireFormat(AppTime.startOfDay(date)),
        endIso = AppTime.toWireFormat(AppTime.startOfNextDay(date))
    )

    /**
     * The default report date for an unopened "today" report — business zone, NEVER device
     * zone. Canonical call site for the "today" default used by `DailyReportScreen` (initial
     * `LaunchedEffect`) and `RouteMapScreen` (initial `selectedDate`), replacing the bare
     * `LocalDate.now()` / the legacy date util's `getCurrentDate()` calls that both anchored to
     * `ZoneId.systemDefault()` (bug #1: a device near midnight in another zone opened the
     * report on the wrong business day).
     */
    fun todayForReport(clock: AppClock = AppClock.System): LocalDate =
        AppTime.todayInBusinessZone(clock)
}
