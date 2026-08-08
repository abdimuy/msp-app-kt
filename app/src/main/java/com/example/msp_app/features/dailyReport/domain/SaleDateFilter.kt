package com.example.msp_app.features.dailyReport.domain

import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.time.AppTime
import java.time.LocalDate

/**
 * Pure, side-effect-free selection of sales that belong to a given business calendar date.
 *
 * Extracted from [com.example.msp_app.features.dailyReport.data.repository.DailyReportRepository]
 * for two reasons:
 *  1. This is the single point where we translate a stored UTC [java.time.Instant] back to the
 *     business-zone calendar date. Concentrating that translation here makes it impossible to
 *     accidentally reintroduce the timezone bug elsewhere in the feature.
 *  2. Being pure, it can be unit tested against every boundary condition (late-night, DST,
 *     malformed input) without touching Room.
 */
internal fun List<LocalSaleEntity>.onBusinessDate(date: LocalDate): List<LocalSaleEntity> =
    filter { sale ->
        val instant = AppTime.parseWireFormatOrNull(sale.FECHA_VENTA) ?: return@filter false
        AppTime.toBusinessDate(instant) == date
    }
