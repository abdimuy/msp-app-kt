package com.example.msp_app.feature.collectionreport.ui.actions.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

// Paleta (RGB, del reporte Go — ver spec del dispatch): declaradas como propiedades top-level
// (exentas de `MagicNumber` por `ignorePropertyDeclaration`) para no repetir literales sueltos
// dentro de cada función de dibujo.
private val COLOR_INK = Color.rgb(26, 26, 26)
private val COLOR_GRAY = Color.rgb(120, 120, 120)
private val COLOR_HAIR = Color.rgb(229, 231, 235)
private val COLOR_SLATE = Color.rgb(51, 65, 85)
private val COLOR_GREEN = Color.rgb(22, 163, 74)
private val COLOR_VIOLET = Color.rgb(124, 77, 196)
private val COLOR_ZEBRA = Color.rgb(247, 247, 249)
private val COLOR_TINT = Color.rgb(241, 242, 244)
private val COLOR_TINT_VIOLET = Color.rgb(246, 242, 251)

// Offsets de baseline / grosores — todos puntos (pt), consistentes con [PdfLayout].
private const val HAIRLINE_THICKNESS = 1f
private const val RULE_THICKNESS = 1.5f
private const val CELL_PADDING = 6f

private const val EYEBROW_BASELINE = 10f
private const val TITLE_BASELINE = 32f
private const val SUBTITLE_BASELINE = 48f
private const val META_LINE1_BASELINE = 12f
private const val META_LINE2_BASELINE = 26f
private const val HEADER_RULE_OFFSET_FROM_BOTTOM = 6f

private const val SUMMARY_LABEL_BASELINE = 18f
private const val SUMMARY_VALUE_BASELINE = 38f
private const val SUMMARY_SEPARATOR_INSET = 8f

private const val SECTION_TITLE_BASELINE = 13f
private const val TABLE_HEADER_BASELINE = 11f
private const val ROW_BASELINE = 10f
private const val NOTE_BASELINE = 9f

private const val RECAP_RULE_OFFSET = 4f
private const val RECAP_LABEL_GAP = 8f

private const val BADGE_PADDING_X = 5f
private const val BADGE_TEXT_RISE = 8f
private const val BADGE_PADDING_BELOW = 3f
private const val BADGE_CORNER_RADIUS = 5f

private const val FOOTER_TEXT_OFFSET = 18f

private fun PdfEmphasis.toColor(): Int = when (this) {
    PdfEmphasis.INK -> COLOR_INK
    PdfEmphasis.GRAY -> COLOR_GRAY
    PdfEmphasis.SLATE -> COLOR_SLATE
    PdfEmphasis.GREEN -> COLOR_GREEN
    PdfEmphasis.VIOLET -> COLOR_VIOLET
}

/**
 * Dibuja de verdad sobre un `Canvas` de `PdfDocument` — el ÚNICO archivo del paquete `pdf` que
 * toca `android.graphics` (todo lo demás — [PdfReportModel], [buildPdfBlocks],
 * [paginatePdfBlocks], [PdfTextWrap] — es Kotlin puro y testeable en JVM). Sin cobertura de
 * unit test a propósito: el shadow de Robolectric para `PdfDocument`/`Canvas` no soporta
 * `startPage`/dibujo real en la versión de este proyecto (ver KDoc de
 * `ReportActionsControllerTest`, mismo límite conocido que ya documentaba el `generatePdf`
 * anterior) — lo que SÍ es testeable (paginación completa, orden, ningún pago/visita perdido,
 * wrap de notas) vive en los archivos puros de arriba.
 */
@Suppress("TooManyFunctions")
internal object PdfCanvasRenderer {

    /** Dibuja UNA página completa: el flujo de [blocks] de arriba a abajo + el pie fijo. */
    fun drawPage(
        canvas: Canvas,
        blocks: List<PdfBlock>,
        pageNumber: Int,
        totalPages: Int,
        footer: PdfFooterModel
    ) {
        var y = PdfLayout.MARGIN
        blocks.forEach { block -> y = draw(canvas, block, y) }
        drawFooter(canvas, footer, pageNumber, totalPages)
    }

    private fun draw(canvas: Canvas, block: PdfBlock, yStart: Float): Float = when (block) {
        is PdfBlock.Header -> drawHeader(canvas, block.model, yStart)
        is PdfBlock.Summary -> drawSummary(canvas, block.model, yStart)
        is PdfBlock.SectionTitle -> drawSectionTitle(canvas, block, yStart)
        PdfBlock.PaymentTableHeader -> drawPaymentTableHeader(canvas, yStart)
        is PdfBlock.PaymentRow -> drawPaymentRow(canvas, block, yStart)
        is PdfBlock.CondonacionRow -> drawCondonacionRow(canvas, block, yStart)
        PdfBlock.TotalsRule -> drawRecapRule(canvas, yStart)
        is PdfBlock.TotalLine -> drawRecapLine(canvas, block.line, yStart)
        is PdfBlock.VisitRow -> drawVisitRow(canvas, block.data, yStart)
        is PdfBlock.Spacer -> yStart + block.height
    }

    private fun drawHeader(canvas: Canvas, model: PdfHeaderModel, yStart: Float): Float {
        val right = PdfLayout.MARGIN + PdfLayout.CONTENT_WIDTH
        canvas.drawText(
            model.eyebrow,
            PdfLayout.MARGIN,
            yStart + EYEBROW_BASELINE,
            textPaint(PdfLayout.EYEBROW_TEXT_SIZE, COLOR_SLATE, bold = true)
        )
        canvas.drawText(
            model.title,
            PdfLayout.MARGIN,
            yStart + TITLE_BASELINE,
            textPaint(PdfLayout.TITLE_TEXT_SIZE, COLOR_INK, bold = true)
        )
        canvas.drawText(
            model.subtitle,
            PdfLayout.MARGIN,
            yStart + SUBTITLE_BASELINE,
            textPaint(PdfLayout.SUBTITLE_TEXT_SIZE, COLOR_GRAY)
        )
        val metaPaint = textPaint(PdfLayout.META_TEXT_SIZE, COLOR_INK).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(model.cobradorLine, right, yStart + META_LINE1_BASELINE, metaPaint)
        canvas.drawText(model.generatedLine, right, yStart + META_LINE2_BASELINE, metaPaint)

        val ruleY = yStart + PdfLayout.HEADER_HEIGHT - HEADER_RULE_OFFSET_FROM_BOTTOM
        canvas.drawRect(
            PdfLayout.MARGIN,
            ruleY,
            right,
            ruleY + RULE_THICKNESS,
            fillPaint(COLOR_SLATE)
        )
        return yStart + PdfLayout.HEADER_HEIGHT
    }

    private fun drawSummary(canvas: Canvas, model: PdfSummaryModel, yStart: Float): Float {
        val left = PdfLayout.MARGIN
        val right = left + PdfLayout.CONTENT_WIDTH
        val bottom = yStart + PdfLayout.SUMMARY_HEIGHT
        val hair = fillPaint(COLOR_HAIR)
        canvas.drawRect(left, yStart, right, yStart + HAIRLINE_THICKNESS, hair)
        canvas.drawRect(left, bottom - HAIRLINE_THICKNESS, right, bottom, hair)

        val cellWidth = PdfLayout.CONTENT_WIDTH / model.cells.size
        model.cells.forEachIndexed { index, cell ->
            val cellLeft = left + cellWidth * index
            if (index > 0) {
                canvas.drawRect(
                    cellLeft,
                    yStart + SUMMARY_SEPARATOR_INSET,
                    cellLeft + HAIRLINE_THICKNESS,
                    bottom - SUMMARY_SEPARATOR_INSET,
                    hair
                )
            }
            val textX = cellLeft + CELL_PADDING
            canvas.drawText(
                cell.label.uppercase(),
                textX,
                yStart + SUMMARY_LABEL_BASELINE,
                textPaint(PdfLayout.SUMMARY_LABEL_TEXT_SIZE, COLOR_GRAY, bold = true)
            )
            canvas.drawText(
                cell.value,
                textX,
                yStart + SUMMARY_VALUE_BASELINE,
                textPaint(
                    PdfLayout.SUMMARY_VALUE_TEXT_SIZE,
                    cell.emphasis.toColor(),
                    bold = true,
                    mono = true
                )
            )
        }
        return bottom
    }

    private fun drawSectionTitle(
        canvas: Canvas,
        block: PdfBlock.SectionTitle,
        yStart: Float
    ): Float {
        canvas.drawText(
            block.text,
            PdfLayout.MARGIN,
            yStart + SECTION_TITLE_BASELINE,
            textPaint(PdfLayout.SECTION_TITLE_TEXT_SIZE, block.emphasis.toColor(), bold = true)
        )
        return yStart + block.height
    }

    private fun drawPaymentTableHeader(canvas: Canvas, yStart: Float): Float {
        val left = PdfLayout.MARGIN
        val right = left + PdfLayout.CONTENT_WIDTH
        val bottom = yStart + PdfLayout.TABLE_HEADER_HEIGHT
        canvas.drawRect(left, yStart, right, bottom, fillPaint(COLOR_TINT))
        val paint = textPaint(PdfLayout.TABLE_HEADER_TEXT_SIZE, COLOR_GRAY, bold = true)
        val baseline = yStart + TABLE_HEADER_BASELINE
        canvas.drawText("FECHA", left + CELL_PADDING, baseline, paint)
        canvas.drawText("CLIENTE", columnClienteX(), baseline, paint)
        canvas.drawText("MÉTODO", columnMetodoX(), baseline, paint)
        val rightPaint = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("IMPORTE", right - CELL_PADDING, baseline, rightPaint)
        return bottom
    }

    private fun drawPaymentRow(canvas: Canvas, block: PdfBlock.PaymentRow, yStart: Float): Float {
        val left = PdfLayout.MARGIN
        val right = left + PdfLayout.CONTENT_WIDTH
        val bottom = yStart + block.height
        if (block.zebra) canvas.drawRect(left, yStart, right, bottom, fillPaint(COLOR_ZEBRA))

        val baseline = yStart + ROW_BASELINE
        val data = block.data
        canvas.drawText(
            data.fecha,
            left + CELL_PADDING,
            baseline,
            textPaint(PdfLayout.ROW_TEXT_SIZE, COLOR_INK, mono = true)
        )

        val clientePaint = textPaint(PdfLayout.ROW_TEXT_SIZE, COLOR_INK)
        val clienteMaxWidth = columnMetodoX() - columnClienteX() - CELL_PADDING
        canvas.drawText(
            ellipsize(data.cliente, clienteMaxWidth, clientePaint),
            columnClienteX(),
            baseline,
            clientePaint
        )

        canvas.drawText(
            data.metodo,
            columnMetodoX(),
            baseline,
            textPaint(PdfLayout.ROW_TEXT_SIZE, data.metodoEmphasis.toColor())
        )

        val importePaint = textPaint(PdfLayout.ROW_TEXT_SIZE, COLOR_INK, mono = true).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(data.importe, right - CELL_PADDING, baseline, importePaint)
        return bottom
    }

    private fun drawCondonacionRow(
        canvas: Canvas,
        block: PdfBlock.CondonacionRow,
        yStart: Float
    ): Float {
        val left = PdfLayout.MARGIN
        val right = left + PdfLayout.CONTENT_WIDTH
        val bottom = yStart + block.height
        if (block.zebra) canvas.drawRect(left, yStart, right, bottom, fillPaint(COLOR_TINT_VIOLET))

        val baseline = yStart + ROW_BASELINE
        val data = block.data
        val label = if (data.motivo.isBlank()) data.cliente else "${data.cliente} · ${data.motivo}"
        val labelPaint = textPaint(PdfLayout.ROW_TEXT_SIZE, COLOR_INK)
        val importePaint = textPaint(PdfLayout.ROW_TEXT_SIZE, COLOR_VIOLET, mono = true).apply {
            textAlign = Paint.Align.RIGHT
        }
        val maxLabelWidth = right - CELL_PADDING - importePaint.measureText(data.importe) -
            RECAP_LABEL_GAP - left - CELL_PADDING
        canvas.drawText(
            ellipsize(label, maxLabelWidth, labelPaint),
            left + CELL_PADDING,
            baseline,
            labelPaint
        )
        canvas.drawText(data.importe, right - CELL_PADDING, baseline, importePaint)
        return bottom
    }

    private fun drawRecapRule(canvas: Canvas, yStart: Float): Float {
        val ruleY = yStart + RECAP_RULE_OFFSET
        canvas.drawRect(
            PdfLayout.MARGIN,
            ruleY,
            PdfLayout.MARGIN + PdfLayout.CONTENT_WIDTH,
            ruleY + RULE_THICKNESS,
            fillPaint(COLOR_SLATE)
        )
        return yStart + PdfLayout.RECAP_RULE_HEIGHT
    }

    private fun drawRecapLine(canvas: Canvas, line: PdfTotalLine, yStart: Float): Float {
        val right = PdfLayout.MARGIN + PdfLayout.CONTENT_WIDTH
        val baseline = yStart + ROW_BASELINE
        val valuePaint = textPaint(
            PdfLayout.RECAP_TEXT_SIZE,
            line.emphasis.toColor(),
            bold = line.bold,
            mono = true
        )
            .apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(line.value, right, baseline, valuePaint)
        val valueWidth = valuePaint.measureText(line.value)
        val labelPaint = textPaint(PdfLayout.RECAP_TEXT_SIZE, COLOR_INK, bold = line.bold).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(line.label, right - valueWidth - RECAP_LABEL_GAP, baseline, labelPaint)
        return yStart + PdfLayout.RECAP_LINE_HEIGHT
    }

    private fun drawVisitRow(canvas: Canvas, data: PdfVisitRowData, yStart: Float): Float {
        var y = yStart
        val left = PdfLayout.MARGIN
        val monoPaint = textPaint(PdfLayout.ROW_TEXT_SIZE, COLOR_INK, mono = true)
        val mainPaint = textPaint(PdfLayout.ROW_TEXT_SIZE, COLOR_INK)
        val baseline1 = y + ROW_BASELINE
        canvas.drawText(data.fecha, left, baseline1, monoPaint)
        val fechaWidth = monoPaint.measureText(data.fecha) + CELL_PADDING
        val clienteMaxWidth = PdfLayout.CONTENT_WIDTH - fechaWidth
        canvas.drawText(
            ellipsize(data.cliente, clienteMaxWidth, mainPaint),
            left + fechaWidth,
            baseline1,
            mainPaint
        )
        y += PdfLayout.VISIT_LINE_HEIGHT

        if (data.tipo.isNotBlank()) {
            drawBadge(canvas, data.tipo, left + PdfLayout.VISIT_NOTE_INDENT, y)
            y += PdfLayout.VISIT_LINE_HEIGHT
        }

        val notePaint = textPaint(PdfLayout.VISIT_NOTE_TEXT_SIZE, COLOR_GRAY)
        data.noteLines.forEach { line ->
            canvas.drawText(line, left + PdfLayout.VISIT_NOTE_INDENT, y + NOTE_BASELINE, notePaint)
            y += PdfLayout.VISIT_LINE_HEIGHT
        }
        return y + PdfLayout.VISIT_ROW_GAP
    }

    private fun drawBadge(canvas: Canvas, label: String, x: Float, y: Float) {
        val paint = textPaint(PdfLayout.VISIT_NOTE_TEXT_SIZE, COLOR_SLATE, bold = true)
        val textWidth = paint.measureText(label)
        val rect =
            RectF(
                x,
                y - BADGE_TEXT_RISE,
                x + textWidth + BADGE_PADDING_X * 2,
                y + BADGE_PADDING_BELOW
            )
        canvas.drawRoundRect(rect, BADGE_CORNER_RADIUS, BADGE_CORNER_RADIUS, fillPaint(COLOR_TINT))
        canvas.drawText(label, x + BADGE_PADDING_X, y, paint)
    }

    private fun drawFooter(
        canvas: Canvas,
        footer: PdfFooterModel,
        pageNumber: Int,
        totalPages: Int
    ) {
        val left = PdfLayout.MARGIN
        val right = left + PdfLayout.CONTENT_WIDTH
        val ruleY = PdfLayout.PAGE_HEIGHT - PdfLayout.FOOTER_RESERVED
        canvas.drawRect(left, ruleY, right, ruleY + HAIRLINE_THICKNESS, fillPaint(COLOR_HAIR))
        val baseline = ruleY + FOOTER_TEXT_OFFSET
        canvas.drawText(
            footer.leftLabel,
            left,
            baseline,
            textPaint(PdfLayout.FOOTER_TEXT_SIZE, COLOR_GRAY)
        )
        val rightPaint = textPaint(PdfLayout.FOOTER_TEXT_SIZE, COLOR_GRAY).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Página $pageNumber de $totalPages", right, baseline, rightPaint)
    }

    private fun columnClienteX(): Float =
        PdfLayout.MARGIN + PdfLayout.COL_FECHA_WIDTH + CELL_PADDING

    private fun columnMetodoX(): Float =
        PdfLayout.MARGIN + PdfLayout.CONTENT_WIDTH - PdfLayout.COL_VALUE_WIDTH - PdfLayout.COL_METODO_WIDTH

    /**
     * Trunca [text] a `…` cuando excede [maxWidth] bajo [paint] — evita que un nombre de
     * cliente largo se meta encima de la columna vecina. NUNCA se usa para la nota de visita
     * (esa se envuelve completa vía [PdfTextWrap], jamás se trunca).
     */
    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.take(end) + ellipsis) > maxWidth) end--
        return if (end <= 0) ellipsis else text.take(end) + ellipsis
    }

    private fun textPaint(
        size: Float,
        color: Int,
        bold: Boolean = false,
        mono: Boolean = false
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = when {
            mono -> Typeface.MONOSPACE
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
    }

    private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
}
