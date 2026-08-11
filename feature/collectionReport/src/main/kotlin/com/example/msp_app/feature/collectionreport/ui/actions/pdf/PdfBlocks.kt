package com.example.msp_app.feature.collectionreport.ui.actions.pdf

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.ui.ForgivenessRowUi
import com.example.msp_app.feature.collectionreport.ui.PaymentRowUi
import com.example.msp_app.feature.collectionreport.ui.VisitRowUi
import java.time.Instant

private const val PDF_DATE_PATTERN = "dd/MM HH:mm"

/** Fila ya formateada a texto/color de un pago — lo único que dibuja [PdfCanvasRenderer]. */
internal data class PdfPaymentRowData(
    val id: String,
    val fecha: String,
    val cliente: String,
    val metodo: String,
    val metodoEmphasis: PdfEmphasis,
    val importe: String
)

internal data class PdfCondonacionRowData(val cliente: String, val motivo: String, val importe: String)

internal data class PdfVisitRowData(
    val fecha: String,
    val cliente: String,
    val tipo: String,
    val noteLines: List<String>
)

/**
 * Un elemento de layout del PDF, en el orden en que debe dibujarse — el "flujo" que
 * [PdfPaginator] parte en páginas. Cada variante ya trae SU altura resuelta (constante para
 * casi todas, calculada para [VisitRow] porque depende del número de líneas envueltas de la
 * nota) para que paginar y dibujar compartan una sola fuente de verdad de layout (ver KDoc de
 * [PdfLayout]).
 */
internal sealed interface PdfBlock {
    val height: Float

    data class Header(val model: PdfHeaderModel) : PdfBlock {
        override val height = PdfLayout.HEADER_HEIGHT
    }

    data class Summary(val model: PdfSummaryModel) : PdfBlock {
        override val height = PdfLayout.SUMMARY_HEIGHT
    }

    data class SectionTitle(val text: String, val emphasis: PdfEmphasis = PdfEmphasis.GRAY) : PdfBlock {
        override val height = PdfLayout.SECTION_TITLE_HEIGHT
    }

    /** Encabezado de columnas de la tabla de pagos — [PdfPaginator] lo repite en cada página. */
    data object PaymentTableHeader : PdfBlock {
        override val height = PdfLayout.TABLE_HEADER_HEIGHT
    }

    data class PaymentRow(val data: PdfPaymentRowData, val zebra: Boolean) : PdfBlock {
        override val height = PdfLayout.PAYMENT_ROW_HEIGHT
    }

    data class CondonacionRow(val data: PdfCondonacionRowData, val zebra: Boolean) : PdfBlock {
        override val height = PdfLayout.CONDONACION_ROW_HEIGHT
    }

    data object TotalsRule : PdfBlock {
        override val height = PdfLayout.RECAP_RULE_HEIGHT
    }

    data class TotalLine(val line: PdfTotalLine) : PdfBlock {
        override val height = PdfLayout.RECAP_LINE_HEIGHT
    }

    data class VisitRow(val data: PdfVisitRowData) : PdfBlock {
        override val height: Float = run {
            val lines = 1 + (if (data.tipo.isNotBlank()) 1 else 0) + data.noteLines.size
            lines * PdfLayout.VISIT_LINE_HEIGHT + PdfLayout.VISIT_ROW_GAP
        }
    }

    data class Spacer(override val height: Float) : PdfBlock
}

/**
 * Convierte [model] en el flujo ordenado de [PdfBlock] a paginar/dibujar. Puro salvo por
 * [measureText] (inyectado — ver KDoc de [PdfTextWrap] sobre por qué el wrap de la nota de
 * visita necesita medir texto proporcional, y por qué eso NO rompe la testeabilidad: en
 * producción [measureText] es `Paint.measureText`, en test es cualquier función determinista).
 *
 * Secciones omitidas enteras cuando vienen vacías (nunca un encabezado sin filas debajo):
 * DETALLE DE PAGOS, CONDONACIONES, VISITAS. Totales SIEMPRE se dibujan (Efectivo/Transferencia
 * en $0 es información real, no una fila inventada).
 */
internal fun buildPdfBlocks(model: PdfReportModel, measureText: (String) -> Float): List<PdfBlock> =
    buildList {
        add(PdfBlock.Header(model.header))
        add(PdfBlock.Summary(model.summary))
        add(PdfBlock.Spacer(PdfLayout.SPACER_MEDIUM))

        if (model.payments.isNotEmpty()) {
            add(PdfBlock.SectionTitle("DETALLE DE PAGOS", PdfEmphasis.GRAY))
            add(PdfBlock.PaymentTableHeader)
            model.payments.forEachIndexed { index, row ->
                add(PdfBlock.PaymentRow(row.toPdfRowData(), zebra = index % 2 == 0))
            }
            add(PdfBlock.Spacer(PdfLayout.SPACER_MEDIUM))
        }

        if (model.condonaciones.isNotEmpty()) {
            add(PdfBlock.SectionTitle("CONDONACIONES", PdfEmphasis.VIOLET))
            model.condonaciones.forEachIndexed { index, row ->
                add(PdfBlock.CondonacionRow(row.toPdfRowData(), zebra = index % 2 == 0))
            }
            add(PdfBlock.Spacer(PdfLayout.SPACER_MEDIUM))
        }

        add(PdfBlock.TotalsRule)
        model.totals.forEach { add(PdfBlock.TotalLine(it)) }
        add(PdfBlock.Spacer(PdfLayout.SPACER_MEDIUM))

        if (model.visits.isNotEmpty()) {
            add(PdfBlock.SectionTitle("VISITAS", PdfEmphasis.GRAY))
            model.visits.forEach { visit ->
                add(
                    PdfBlock.VisitRow(visit.toPdfRowData(measureText))
                )
            }
        }
    }

private fun PaymentRowUi.toPdfRowData(): PdfPaymentRowData = PdfPaymentRowData(
    id = id,
    fecha = formatFecha(paidAt),
    cliente = cliente,
    metodo = metodoLabel(method),
    metodoEmphasis = metodoEmphasis(method),
    importe = money(amount)
)

private fun ForgivenessRowUi.toPdfRowData(): PdfCondonacionRowData =
    PdfCondonacionRowData(cliente = cliente, motivo = motivo, importe = money(amount))

private fun VisitRowUi.toPdfRowData(measureText: (String) -> Float): PdfVisitRowData =
    PdfVisitRowData(
        fecha = formatFecha(visitedAt),
        cliente = cliente,
        tipo = tipo,
        noteLines = PdfTextWrap.wrap(nota, PdfLayout.VISIT_NOTE_MAX_WIDTH, measureText)
    )

private fun formatFecha(instant: Instant): String = AppTime.formatForDisplay(
    instant,
    PDF_DATE_PATTERN
)

private fun metodoLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.EFECTIVO -> "Efectivo"
    PaymentMethod.CHEQUE -> "Cheque"
    PaymentMethod.TRANSFERENCIA -> "Transferencia"
    PaymentMethod.CONDONACION -> "Condonación"
    PaymentMethod.OTRO -> "Otro"
}

private fun metodoEmphasis(method: PaymentMethod): PdfEmphasis = when (method) {
    PaymentMethod.EFECTIVO -> PdfEmphasis.GREEN
    PaymentMethod.TRANSFERENCIA -> PdfEmphasis.SLATE
    PaymentMethod.CONDONACION -> PdfEmphasis.VIOLET
    PaymentMethod.CHEQUE, PaymentMethod.OTRO -> PdfEmphasis.GRAY
}

private fun money(amount: Money): String = formatMoneyMxn(amount.amount)
