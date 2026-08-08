package com.example.msp_app.features.dailyReport.domain

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.features.transfers.data.api.dto.TransferListItemDto
import java.time.LocalDate

/**
 * Pure filter for transfer DTOs that belong to a given business calendar date.
 *
 * The backend may interpret the `fechaInicio`/`fechaFin` query parameters in any
 * timezone (typically UTC). To be robust against that, the caller fetches a window
 * that brackets the target date (yesterday..tomorrow) and this filter narrows the
 * result to the business-zone day. This eliminates the entire class of "transfer
 * made after 18:00 CDMX shows up in the wrong report" bugs regardless of server
 * behavior.
 *
 * Transfers with a missing or malformed `fechaHoraCreacion` are excluded silently.
 */
internal fun List<TransferListItemDto>.onBusinessDate(date: LocalDate): List<TransferListItemDto> =
    filter { transfer ->
        val iso = transfer.fechaHoraCreacion ?: return@filter false
        val instant = AppTime.parseWireFormatOrNull(iso) ?: return@filter false
        AppTime.toBusinessDate(instant) == date
    }
