package com.example.msp_app.feature.collectionreport.ui.components

import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.VisitRowUi

/**
 * Cuerpo del sheet `SheetKind.VISITAS`, separado de `ReportSheetContent.kt` SOLO para no
 * cruzar el umbral `TooManyFunctions` de detekt en ese archivo — mismo criterio que separó
 * `SheetPaymentRow.kt`. `internal` (no `private`): [deriveSheetContent] en
 * `ReportSheetContent.kt` lo llama desde otro archivo del mismo paquete.
 */

/**
 * Fix de dispositivo (Task 2): [VisitRowUi.tipo] (el motivo/resultado elegido al capturar la
 * visita) se antepone a la nota del cobrador en el subtítulo, separado por `·` — mismo
 * separador que ya usa `methodSheet`/`diaCicloSheet` para dos datos en una línea. Cuando
 * alguno de los dos llega vacío la fila cae al otro solo, nunca un `·` colgando.
 */
internal fun visitasSheet(state: CollectionReportUiState): SheetContentUi {
    val subtitle = "${state.visitas.count ?: 0} visitas"
    val rows = state.visitRows.map { row ->
        SheetRowUi(
            title = row.cliente,
            subtitle = row.subtitleText()
        )
    }
    return SheetContentUi("Visitas", subtitle, rows)
}

private fun VisitRowUi.subtitleText(): String? =
    listOf(tipo, nota).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }
