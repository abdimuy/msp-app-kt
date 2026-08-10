package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.TicketLine

/**
 * Flattens a semantic [PrintableTicket] into the fixed-width plain-text lines a
 * thermal printer prints. Pure and width-aware: it expands the presentation-only
 * lines — [TicketLine.Separator] into a full-width rule, [TicketLine.Blank] into
 * an empty line, and [TicketLine.ColumnRow] into a weighted column layout — while
 * text lines already carry width-baked content from the formatter and pass
 * through verbatim.
 *
 * The T2 DantSu adapter reuses this to obtain the print strings; it only adds the
 * `<b>` emphasis for [TicketLine.Header]/[TicketLine.Bold] on top.
 */
object TicketRenderer {
    /** Renders every line of [ticket] to a plain-text string at [profile]'s width. */
    fun render(ticket: PrintableTicket, profile: PrinterProfile): List<String> =
        ticket.map { renderLine(it, profile.charsPerLine) }

    private fun renderLine(line: TicketLine, width: Int): String = when (line) {
        is TicketLine.Header -> line.text
        is TicketLine.CenteredLine -> line.text
        is TicketLine.Bold -> line.text
        is TicketLine.Line -> line.text
        TicketLine.Blank -> ""
        is TicketLine.Separator -> line.char.toString().repeat(width)
        is TicketLine.ColumnRow -> renderColumns(line.cols, line.weights, width)
    }

    private fun renderColumns(cols: List<String>, weights: List<Int>, width: Int): String {
        require(cols.isNotEmpty() && cols.size == weights.size) {
            "cols and weights must be non-empty and the same size"
        }
        val totalWeight = weights.sum()
        val out = StringBuilder()
        var used = 0
        for (i in cols.indices) {
            val isLast = i == cols.lastIndex
            val cellWidth = if (isLast) width - used else width * weights[i] / totalWeight
            used += cellWidth
            val cell = cols[i].take(cellWidth)
            out.append(if (isLast) cell.padStart(cellWidth) else cell.padEnd(cellWidth))
        }
        return out.toString()
    }
}
