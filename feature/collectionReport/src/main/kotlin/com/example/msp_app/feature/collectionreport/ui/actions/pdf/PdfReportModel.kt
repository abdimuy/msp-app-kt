package com.example.msp_app.feature.collectionreport.ui.actions.pdf

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.DetailSort
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.ForgivenessRowUi
import com.example.msp_app.feature.collectionreport.ui.PaymentRowUi
import com.example.msp_app.feature.collectionreport.ui.VisitRowUi

/** Color semántico de un texto/monto del PDF (paleta del reporte Go, ver KDoc del dispatch). */
internal enum class PdfEmphasis { INK, GRAY, SLATE, GREEN, VIOLET }

private const val EYEBROW = "MUEBLERÍA MSP · COBRANZA"
private const val TITLE = "Reporte de cobranza"

internal data class PdfHeaderModel(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val cobradorLine: String,
    val generatedLine: String
)

internal data class PdfSummaryCell(val label: String, val value: String, val emphasis: PdfEmphasis)

internal data class PdfTotalLine(val label: String, val value: String, val emphasis: PdfEmphasis, val bold: Boolean)

internal data class PdfFooterModel(val leftLabel: String)

/**
 * Modelo puro (SIN `android.graphics`) del contenido completo del PDF de cobranza — la fuente
 * única de datos que [buildPdfBlocks] convierte en bloques de layout y que
 * [com.example.msp_app.feature.collectionreport.ui.actions.pdf.PdfPaginator] reparte en
 * páginas. [payments] YA viene ordenado por `state.sort` (Task del dispatch: el PDF debe
 * respetar el mismo orden Hora/Nombre que el tablero y el ticket térmico).
 */
internal data class PdfReportModel(
    val header: PdfHeaderModel,
    val summary: PdfSummaryModel,
    val payments: List<PaymentRowUi>,
    val condonaciones: List<ForgivenessRowUi>,
    val totals: List<PdfTotalLine>,
    val visits: List<VisitRowUi>,
    val footer: PdfFooterModel
)

internal data class PdfSummaryModel(val cells: List<PdfSummaryCell>)

/**
 * Construye [PdfReportModel] a partir de [state] — mismo contrato de datos que consume el
 * ticket térmico ([com.example.msp_app.feature.collectionreport.printing.CollectionReportFormatter])
 * y los sheets del tablero
 * ([com.example.msp_app.feature.collectionreport.ui.components.ReportSheetContent]): TODOS los
 * pagos vía `state.allPayments()` (Día directo de `detail`, Semana aplanando
 * `state.dayPayments` — la extensión vive `private` en `ReportSheetContent.kt`, así que se
 * repite aquí el mismo one-liner, MISMA convención de duplicar helpers pequeños que ya
 * documenta `CollectionReportFormatter`), condonaciones de `state.condonadoRows`, visitas de
 * `state.visitRows`.
 */
internal fun buildPdfReportModel(state: CollectionReportUiState, clock: AppClock): PdfReportModel {
    val payments = sortedPayments(allPayments(state), state.sort)
    return PdfReportModel(
        header = buildHeader(state, clock),
        summary = buildSummary(state, payments),
        payments = payments,
        condonaciones = state.condonadoRows,
        totals = buildTotals(state),
        visits = state.visitRows,
        footer = PdfFooterModel(
            leftLabel = "Generado por ${state.cobrador} · ${generatedAt(clock)}"
        )
    )
}

private fun allPayments(state: CollectionReportUiState): List<PaymentRowUi> =
    (state.detail as? DetailUi.Payments)?.rows ?: state.dayPayments.flatten()

private fun sortedPayments(payments: List<PaymentRowUi>, sort: DetailSort): List<PaymentRowUi> =
    when (sort) {
        DetailSort.HORA -> payments.sortedBy { it.paidAt }
        DetailSort.NOMBRE -> payments.sortedBy { it.cliente.lowercase() }
    }

private fun buildHeader(state: CollectionReportUiState, clock: AppClock): PdfHeaderModel {
    val periodWord = when (state.period) {
        ReportPeriod.DIA -> "Día"
        ReportPeriod.SEMANA -> "Semana"
    }
    return PdfHeaderModel(
        eyebrow = EYEBROW,
        title = TITLE,
        subtitle = "$periodWord · ${state.rangeLabel}",
        cobradorLine = "Cobrador: ${state.cobrador}",
        generatedLine = "Generado: ${generatedAt(clock)}"
    )
}

private fun generatedAt(clock: AppClock): String =
    AppTime.formatForDisplay(clock.now(), AppTime.Formats.DATE_TIME_24H)

private fun buildSummary(
    state: CollectionReportUiState,
    payments: List<PaymentRowUi>
): PdfSummaryModel = PdfSummaryModel(
    cells = listOf(
        PdfSummaryCell("Total pagos", payments.size.toString(), PdfEmphasis.INK),
        PdfSummaryCell("Recaudado", money(state.hero.monto), PdfEmphasis.INK),
        PdfSummaryCell(
            "Visitas",
            (state.visitas.count ?: state.visitRows.size).toString(),
            PdfEmphasis.INK
        ),
        PdfSummaryCell(
            "Condonado",
            money(state.condonado.amount ?: Money.ZERO),
            PdfEmphasis.VIOLET
        )
    )
)

/**
 * Efectivo/Transferencia SIEMPRE presentes (con su conteo); Condonado solo cuando hay
 * condonaciones cargadas (nunca una línea "Condonado (0)" inventada — mismo criterio que
 * `CollectionReportFormatter.addTotalsBlock`); "Total recaudado" en negritas al final.
 */
private fun buildTotals(state: CollectionReportUiState): List<PdfTotalLine> = buildList {
    add(
        PdfTotalLine(
            "Efectivo (${state.efectivo.count})",
            money(state.efectivo.amount),
            PdfEmphasis.SLATE,
            bold = false
        )
    )
    add(
        PdfTotalLine(
            "Transferencia (${state.transferencia.count})",
            money(state.transferencia.amount),
            PdfEmphasis.SLATE,
            bold = false
        )
    )
    if (state.condonadoRows.isNotEmpty()) {
        add(
            PdfTotalLine(
                "Condonado (${state.condonadoRows.size})",
                money(state.condonado.amount ?: Money.ZERO),
                PdfEmphasis.VIOLET,
                bold = false
            )
        )
    }
    add(PdfTotalLine("Total recaudado", money(state.hero.monto), PdfEmphasis.INK, bold = true))
}

private fun money(amount: Money): String = formatMoneyMxn(amount.amount)
