package com.example.msp_app.features.sales.viewmodels

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime

/**
 * Wire-format "última sincronización" timestamp persisted by [SalesViewModel.syncSales] into
 * the `sync_prefs`/`last_sync_date` `SharedPreferences` entry, and read back for display by
 * `formatLastSyncForDisplay` in
 * `com.example.msp_app.features.home.components.homestartweeksection`.
 *
 * **Task 12 — bug #8 fix (round-trip locale).** Previously the write side formatted with
 * `SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())` and the read side re-parsed
 * with an independently-obtained `Locale.getDefault()` — a double round-trip through the
 * device's mutable default locale (and its calendar system: some locales, e.g. Thai, switch
 * `SimpleDateFormat` to a non-Gregorian calendar internally). If the device's default locale
 * changed between sync and display — or simply behaved differently under a locale using a
 * different calendar/digit system — the read side could silently fail to parse or produce a
 * wrong value.
 *
 * Persisting the canonical UTC wire format instead ([AppTime.toWireFormat]) removes the
 * coupling entirely: the wire format never consults `Locale.getDefault()`, so the pair is
 * locale-independent by construction, matching every other persisted timestamp in the app
 * (`docs/standards/timezones.md`).
 */
fun currentSalesLastSync(clock: AppClock = AppClock.System): String =
    AppTime.toWireFormat(clock.now())
