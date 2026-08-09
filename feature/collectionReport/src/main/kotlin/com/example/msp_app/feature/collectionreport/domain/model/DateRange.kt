package com.example.msp_app.feature.collectionreport.domain.model

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Rango temporal half-open `[startIso, endExclusiveIso)` en formato wire
 * (RFC3339 UTC, p. ej. `2026-08-07T06:00:00Z`), listo para enviarse al backend
 * Go que espera `[desde, hasta)` con fin EXCLUSIVO.
 *
 * Los bordes se calculan siempre en zona negocio (`America/Mexico_City`) vía
 * [AppTime]; aquí se guardan ya serializados a UTC. Los `helpers de etiqueta`
 * ([dayLabel], [cycleLabel]) reconstruyen las fechas de negocio y las formatean
 * en es-MX para la UI.
 */
data class DateRange(
    val startIso: String,
    val endExclusiveIso: String
) {

    /** Primer día incluido, en zona negocio. */
    val startDate: LocalDate
        get() = AppTime.toBusinessDate(AppTime.parseWireFormat(startIso))

    /** Día del borde EXCLUSIVO (no incluido), en zona negocio. */
    val endExclusiveDate: LocalDate
        get() = AppTime.toBusinessDate(AppTime.parseWireFormat(endExclusiveIso))

    /** Último día realmente incluido = borde exclusivo menos un día. */
    val endInclusiveDate: LocalDate
        get() = endExclusiveDate.minusDays(1)

    /** Número de días de negocio que cubre el rango (>= 1 para rangos válidos). */
    val days: Int
        get() = ChronoUnit.DAYS.between(startDate, endExclusiveDate).toInt()

    /** Etiqueta del día final incluido, p. ej. `viernes 7 ago 2026`. */
    fun dayLabel(): String = endInclusiveDate.format(DAY_LABEL_FORMAT)

    /**
     * Etiqueta del ciclo, p. ej. `semana · lun 3 – vie 7 ago · 5 días`.
     *
     * - Un solo día: `semana · vie 7 ago · 1 día` (sin rango redundante).
     * - Mismo mes/año: el inicio omite el mes (`lun 3`), el fin lo lleva.
     * - Distinto mes o año: ambos extremos llevan mes.
     */
    fun cycleLabel(): String {
        val count = days
        val unit = if (count == 1) "día" else "días"
        val start = startDate
        val end = endInclusiveDate
        val span = when {
            start == end -> end.format(END_FORMAT)
            start.month == end.month && start.year == end.year ->
                "${start.format(START_SHORT_FORMAT)} – ${end.format(END_FORMAT)}"
            else -> "${start.format(END_FORMAT)} – ${end.format(END_FORMAT)}"
        }
        return "semana · $span · $count $unit"
    }

    private companion object {
        /** `viernes 7 ago 2026` — día completo con año. */
        val DAY_LABEL_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE d MMM yyyy", BUSINESS_LOCALE)

        /** `lun 3` — abreviado sin mes (extremo inicial del mismo mes). */
        val START_SHORT_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE d", BUSINESS_LOCALE)

        /** `vie 7 ago` — abreviado con mes. */
        val END_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE d MMM", BUSINESS_LOCALE)
    }
}
