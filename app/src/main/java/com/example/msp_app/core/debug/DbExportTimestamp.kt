package com.example.msp_app.core.debug

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime

/**
 * File-name timestamp segment for local DB export copies (`msp_db_<email>_<timestamp>.db`),
 * used by [DbExportManager.createTempCopy]. Extracted as a pure function so it is
 * unit-testable without Android's `Context`/Firebase, which [DbExportManager] depends on.
 *
 * **Task 12 — SimpleDateFormat/Locale.getDefault() sweep.** Replaces
 * `SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())`. Business-locale
 * fixed via [AppTime.formatForDisplay] — never `Locale.getDefault()`
 * (`docs/standards/timezones.md`).
 */
fun dbExportTimestamp(clock: AppClock = AppClock.System): String =
    AppTime.formatForDisplay(clock.now(), "yyyyMMdd_HHmmss")
