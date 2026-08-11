package com.example.msp_app.feature.collectionreport.ui.components

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.collectionreport.ui.PaymentRowUi

/**
 * Helpers de fila de pago de sheet, separados de `ReportSheetContent.kt` SOLO para no cruzar
 * el umbral `TooManyFunctions` de detekt en ese archivo — mismo criterio que separó
 * `MethodPill`/`MethodTile` de `DetailList.kt`.
 */

/**
 * Fila de pago enriquecida (Task 1): título = cliente, subtítulo = "Folio {folio} · HH:mm"
 * (solo la hora cuando [PaymentRowUi.folio] está vacío — el folio nunca se inventa), monto a
 * la derecha, [PaymentRowUi.method] para el tile tintado y [PaymentRowUi.saldo]/
 * [PaymentRowUi.synced] para la tercera línea "Saldo $X" / el chip "Por subir" — mismo
 * criterio que `DetailList.PaymentRow`, sin duplicar la lógica de formateo de hora.
 */
internal fun paymentSheetRow(row: PaymentRowUi): SheetRowUi {
    val hora = AppTime.formatForDisplay(row.paidAt, AppTime.Formats.TIME_24H)
    val subtitle = if (row.folio.isNotBlank()) "Folio ${row.folio} · $hora" else hora
    return SheetRowUi(
        title = row.cliente,
        subtitle = subtitle,
        amount = row.amount,
        method = row.method,
        saldo = row.saldo,
        synced = row.synced
    )
}

/** "María López Hernández" -> "ML" — mismo cálculo que el avatar de iniciales del piloto. */
internal fun clienteInitials(nombre: String): String {
    val palabras = nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        palabras.isEmpty() -> ""
        palabras.size == 1 -> palabras[0].take(1).uppercase()
        else -> "${palabras[0].first()}${palabras[1].first()}".uppercase()
    }
}
