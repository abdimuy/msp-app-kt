package com.example.msp_app.core.logging

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime

/**
 * Human-readable `timestampString` embedded alongside the authoritative Firebase
 * `Timestamp.now()` in [RemoteLogger.log] entries — purely a readable duplicate kept for the
 * Firestore console; the real source of truth stays `Timestamp.now()`. Extracted as a pure
 * function so it is unit-testable without Firebase/Android, which [RemoteLogger] depends on.
 *
 * **Task 12 — SimpleDateFormat/Locale.getDefault() sweep.** Replaces
 * `SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())`.
 * Business-locale fixed via [AppTime.formatForDisplay] — never `Locale.getDefault()`
 * (`docs/standards/timezones.md`).
 */
fun remoteLoggerTimestampString(clock: AppClock = AppClock.System): String =
    AppTime.formatForDisplay(clock.now(), "yyyy-MM-dd HH:mm:ss")
