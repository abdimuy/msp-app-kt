package com.example.msp_app.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Zona fija del negocio. Toda conversión calendario↔instant pasa por aquí. */
val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Mexico_City")

/** Locale fijo del negocio. */
val BUSINESS_LOCALE: Locale = Locale("es", "MX")

/**
 * Single entry point for date/time operations in the app.
 *
 * Core contract:
 *  - Timestamps (`Instant`) are stored and transmitted in UTC, always.
 *  - Calendar dates (`LocalDate`) are interpreted in [BUSINESS_ZONE], always.
 *  - Display layer converts to business zone; business logic compares in business zone.
 *  - `now()` is obtained via [AppClock]; never call `Instant.now()` / `LocalDate.now()` directly.
 *
 * See `docs/standards/timezones.md`.
 */
object AppTime {

    /**
     * Common display patterns. Use these constants instead of hard-coding patterns
     * at call sites so formatting stays consistent across the app.
     */
    object Formats {
        const val ISO_DATE: String = "yyyy-MM-dd"
        const val DATE_SHORT: String = "dd/MM/yyyy"
        const val DATE_TIME_24H: String = "dd/MM/yyyy HH:mm"
        const val DATE_TIME_12H: String = "dd/MM/yyyy hh:mm a"
        const val TIME_24H: String = "HH:mm"
        const val TIME_12H: String = "hh:mm a"
        const val DATE_LONG: String = "EEEE, d 'de' MMMM 'de' yyyy"
    }

    // region — Wire / storage format

    /**
     * Serialize an [Instant] as the canonical wire / storage format
     * (ISO 8601 UTC with `Z`, e.g. `2026-04-16T02:30:15Z`).
     *
     * This is the ONLY format the app emits for timestamps. Room columns, Firestore
     * fields, Retrofit request bodies — all use this.
     */
    fun toWireFormat(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    /**
     * Liberal parser for incoming ISO 8601 strings. Accepts:
     *  - `2026-04-16T02:30:15Z` (preferred)
     *  - `2026-04-16T02:30:15.123Z`
     *  - `2026-04-16T02:30:15-06:00` (with offset)
     *  - `2026-04-16T02:30:15` (no zone — interpreted in [BUSINESS_ZONE], legacy)
     *  - `2026-04-16` (date only — interpreted as midnight in [BUSINESS_ZONE], legacy)
     *
     * @throws DateTimeParseException if [iso] is blank or malformed beyond all four shapes.
     */
    fun parseWireFormat(iso: String): Instant {
        if (iso.isBlank()) {
            throw DateTimeParseException("Blank ISO string", iso, 0)
        }
        // Ordered from strictest to most lenient.
        return parseAs { Instant.parse(iso) }
            ?: parseAs { OffsetDateTime.parse(iso).toInstant() }
            ?: parseAs { LocalDateTime.parse(iso).atZone(BUSINESS_ZONE).toInstant() }
            ?: parseAs { LocalDate.parse(iso).atStartOfDay(BUSINESS_ZONE).toInstant() }
            ?: throw DateTimeParseException("Unrecognised ISO shape: '$iso'", iso, 0)
    }

    /**
     * Graceful variant: returns null for nulls, blanks, or malformed input.
     * Use when defending against dirty backend data; prefer [parseWireFormat]
     * when you have a contract.
     */
    fun parseWireFormatOrNull(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return runCatching { parseWireFormat(iso) }.getOrNull()
    }

    /**
     * Serialize a [LocalDate] to wire format (`yyyy-MM-dd`).
     * This is the format used for calendar-only fields (DIA_COBRANZA, fechaInicio/fechaFin
     * in range queries, etc.) — never a timestamp.
     */
    fun toWireDate(date: LocalDate): String = date.toString()

    /**
     * Parse a wire date (`yyyy-MM-dd`). Returns null for null/blank/malformed.
     */
    fun parseWireDateOrNull(date: String?): LocalDate? {
        if (date.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(date) }.getOrNull()
    }

    // endregion

    // region — Instant ↔ business zone

    /** Convert an [Instant] to the calendar date observed in [BUSINESS_ZONE]. */
    fun toBusinessDate(instant: Instant): LocalDate = instant.atZone(BUSINESS_ZONE).toLocalDate()

    /** Convert an [Instant] to wall-clock date-time observed in [BUSINESS_ZONE]. */
    fun toBusinessDateTime(instant: Instant): LocalDateTime =
        instant.atZone(BUSINESS_ZONE).toLocalDateTime()

    /** Zoned form — use only when UI or logic needs the zone explicitly. */
    fun toBusinessZoned(instant: Instant): ZonedDateTime = instant.atZone(BUSINESS_ZONE)

    /** Today's date in [BUSINESS_ZONE] — NOT the device zone. */
    fun todayInBusinessZone(clock: AppClock = AppClock.System): LocalDate =
        toBusinessDate(clock.now())

    /** Current wall-clock time in [BUSINESS_ZONE]. */
    fun nowInBusinessZone(clock: AppClock = AppClock.System): LocalDateTime =
        toBusinessDateTime(clock.now())

    /** Midnight (00:00:00) of [date] in [BUSINESS_ZONE], as an [Instant]. */
    fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(BUSINESS_ZONE).toInstant()

    /** Start of the next day — useful as exclusive upper bound for a day range. */
    fun startOfNextDay(date: LocalDate): Instant = startOfDay(date.plusDays(1))

    // endregion

    // region — Comparisons / queries

    /** Does this instant fall on today (business zone)? */
    fun isToday(instant: Instant, clock: AppClock = AppClock.System): Boolean =
        toBusinessDate(instant) == todayInBusinessZone(clock)

    /** Does this instant fall on the given calendar date (business zone)? */
    fun isOn(instant: Instant, date: LocalDate): Boolean = toBusinessDate(instant) == date

    /**
     * Is this instant within the current business week (Monday 00:00 ≤ t < next Monday 00:00)?
     */
    fun isThisWeek(instant: Instant, clock: AppClock = AppClock.System): Boolean {
        val today = todayInBusinessZone(clock)
        val monday = today.with(DayOfWeek.MONDAY)
        val d = toBusinessDate(instant)
        return !d.isBefore(monday) && d.isBefore(monday.plusWeeks(1))
    }

    // endregion

    // region — Display formatting (last mile)

    /** Format an [Instant] in business zone for UI. */
    fun formatForDisplay(
        instant: Instant,
        pattern: String = Formats.DATE_TIME_24H,
        locale: Locale = BUSINESS_LOCALE
    ): String = DateTimeFormatter
        .ofPattern(pattern, locale)
        .withZone(BUSINESS_ZONE)
        .format(instant)

    /** Format a [LocalDate] for UI. */
    fun formatDate(
        date: LocalDate,
        pattern: String = Formats.DATE_SHORT,
        locale: Locale = BUSINESS_LOCALE
    ): String = date.format(DateTimeFormatter.ofPattern(pattern, locale))

    /**
     * Convenience: accept a raw wire string (e.g. read from Room) and format for UI.
     * Returns the original string if parsing fails — avoids crashing the UI on bad data,
     * but this is a last line of defense, not a substitute for parsing cleanly upstream.
     */
    fun formatIsoForDisplay(
        iso: String?,
        pattern: String = Formats.DATE_TIME_24H,
        locale: Locale = BUSINESS_LOCALE
    ): String {
        if (iso.isNullOrBlank()) return ""
        return parseWireFormatOrNull(iso)
            ?.let { formatForDisplay(it, pattern, locale) }
            ?: iso
    }

    // endregion

    private inline fun parseAs(block: () -> Instant): Instant? = try {
        block()
    } catch (_: DateTimeParseException) {
        null
    }
}
