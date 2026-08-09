package com.example.msp_app.features.home.components.homestartweeksection

import com.example.msp_app.core.common.time.AppTime

/**
 * Formats the wire-format "última sincronización" timestamp persisted by
 * [com.example.msp_app.features.sales.viewmodels.currentSalesLastSync] /
 * `SalesViewModel.syncSales` for display in [HomeStartWeekSection].
 *
 * **Task 12 — bug #8 fix (round-trip locale).** Replaces a double `SimpleDateFormat(...,
 * Locale.getDefault())` round-trip (parse with one locale-dependent formatter, reformat with
 * another, independently re-reading the device default locale on each side) with
 * [AppTime.formatIsoForDisplay], which parses the canonical UTC wire format and renders with a
 * fixed `BUSINESS_LOCALE` (es-MX). The pair (write side: [currentSalesLastSync]; read side:
 * this function) no longer breaks if the device's default locale changes between sync and
 * display, and never depends on `Locale.getDefault()` (`docs/standards/timezones.md`).
 *
 * Extracted as a top-level pure function (not inlined in the `@Composable`) so it is
 * unit-testable directly.
 */
fun formatLastSyncForDisplay(lastSyncDate: String): String {
    if (lastSyncDate.isBlank()) return "No se ha sincronizado aún"
    return AppTime.formatIsoForDisplay(lastSyncDate, AppTime.Formats.DATE_TIME_12H)
}
