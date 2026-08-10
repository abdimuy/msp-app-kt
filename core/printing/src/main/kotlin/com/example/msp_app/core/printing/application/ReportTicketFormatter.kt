package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.ReportPaymentLine
import com.example.msp_app.core.printing.domain.ReportTicket
import com.example.msp_app.core.printing.domain.TicketLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Turns a [ReportTicket] into a width-aware [PrintableTicket]. **Pure** — no
 * Android, no I/O, no money arithmetic (amounts arrive pre-formatted), same
 * purity contract as [PaymentReceiptFormatter]; the layout helpers
 * (`center`/`twoCol`/`wrap`) are intentionally duplicated rather than shared
 * — this formatter and the payment one are the only two callers and each
 * stays a self-contained, independently-testable unit (mirrors the existing
 * convention: [PaymentReceiptFormatter] keeps its own private copies too).
 *
 * Section order: business + branch → title → period range → **DETALLE DE
 * PAGOS** (Track 2: one row per [ReportTicket.payments], name wrapped + amount
 * right-aligned, prefixed by hora/fecha+hora per [ReportTicket.isWeekly];
 * omitted whole when empty) → **POR MÉTODO** (one [TicketLine.ColumnRow] per
 * method, the reserved column type `TicketLine`'s kdoc calls out for "other
 * ticket types") → receipt count → total (bold) → coverage (omitted when
 * `null`) → collector → notes (omitted when blank) → signature rule → closing
 * header. Serves both the **diario** and **corte** tickets — callers
 * (`ReportesViewModel`) only vary
 * [ReportTicket.title]/[ReportTicket.rangeLabel]/[ReportTicket.coverageLabel]/
 * [ReportTicket.notes]/[ReportTicket.payments]/[ReportTicket.isWeekly] between
 * the two.
 */
class ReportTicketFormatter
@Inject
constructor() {
    fun format(ticket: ReportTicket, profile: PrinterProfile): PrintableTicket {
        val width = profile.charsPerLine
        return buildList {
            add(TicketLine.Header(center(ticket.negocio, width)))
            if (ticket.sucursal.isNotBlank()) {
                add(TicketLine.CenteredLine(center(ticket.sucursal, width)))
            }
            add(TicketLine.Separator())

            add(TicketLine.Header(center(ticket.title, width)))
            add(TicketLine.Line(twoCol(LABEL_PERIODO, ticket.rangeLabel, width)))
            add(TicketLine.Separator())

            addPaymentsBlock(ticket, width)

            add(TicketLine.Header(center(LABEL_METODOS, width)))
            if (ticket.methodRows.isEmpty()) {
                add(TicketLine.CenteredLine(center(EMPTY_METHODS, width)))
            } else {
                ticket.methodRows.forEach { row ->
                    add(
                        TicketLine.ColumnRow(
                            cols = listOf(row.method, "x${row.count}", row.amount),
                            weights = COLUMN_WEIGHTS
                        )
                    )
                }
            }
            add(TicketLine.Separator())

            add(TicketLine.Line(twoCol(LABEL_RECIBOS, "${ticket.paymentCount}", width)))
            add(TicketLine.Bold(twoCol(ticket.totalLabel, ticket.totalAmount, width)))
            ticket.coverageLabel?.let { coverage ->
                add(
                    TicketLine.CenteredLine(center(coverage, width))
                )
            }
            add(TicketLine.Separator())

            add(TicketLine.Line(twoCol(LABEL_COBRO, ticket.collector, width)))
            ticket.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                wrap("$LABEL_NOTAS $notes", width).forEach { add(TicketLine.Line(it)) }
            }
            add(TicketLine.Blank)
            add(TicketLine.Separator())
            add(TicketLine.CenteredLine(center(SIGNATURE_CAPTION, width)))
            add(TicketLine.Blank)

            add(TicketLine.Header(center(CLOSING_TITLE, width)))
        }
    }

    /**
     * The `DETALLE DE PAGOS` section (Track 2): one row per [ReportTicket.payments],
     * newest-first (the caller's order preserved), closed by a separator. Omitted
     * whole — header and separator included — when there are no payments in the
     * window, mirroring how every other optional block on this ticket degrades
     * (`POR MÉTODO`'s own `EMPTY_METHODS` placeholder already covers the empty case
     * for the total/breakdown, so this section adds nothing when there is nothing
     * to list).
     */
    private fun MutableList<TicketLine>.addPaymentsBlock(ticket: ReportTicket, width: Int) {
        if (ticket.payments.isEmpty()) return
        add(TicketLine.Bold(LABEL_DETALLE_PAGOS))
        ticket.payments.forEach { payment -> addPaymentLine(payment, ticket.isWeekly, width) }
        add(TicketLine.Separator())
    }

    /**
     * Renders one payment row: `"<prefix> <cliente>"` word-wrapped to [width] with
     * the pre-formatted amount right-aligned on the first wrapped line (kept on its
     * own line when the wrapped text already fills the width) — mirrors
     * [PaymentReceiptFormatter.addProductLines]'s wrap-then-place-amount layout.
     */
    private fun MutableList<TicketLine>.addPaymentLine(
        payment: ReportPaymentLine,
        isWeekly: Boolean,
        width: Int
    ) {
        val label = "${formatPaymentPrefix(
            payment.recordedAtEpochMillis,
            isWeekly
        )} ${payment.cliente}"
        val labelLines = wrap(label, width)
        val amountFitsOnFirst = labelLines.first().length + 1 + payment.monto.length <= width
        labelLines.forEachIndexed { index, line ->
            if (index == 0 && amountFitsOnFirst) {
                add(TicketLine.Line(twoCol(line, payment.monto, width)))
            } else {
                add(TicketLine.Line(line))
            }
        }
        if (!amountFitsOnFirst) {
            add(TicketLine.Line(twoCol("", payment.monto, width)))
        }
    }

    /**
     * Hora (`HH:mm`) for the diario ticket, fecha+hora (`dd/MM HH:mm`) for the
     * corte/semana ticket — device-local zone (the ticket shows when the
     * collector's device recorded it, the same device-local formatting discipline
     * the receipt side applies to its own `fechaHora`/history rows).
     */
    private fun formatPaymentPrefix(epochMillis: Long, isWeekly: Boolean): String {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        val formatter = if (isWeekly) PAYMENT_DATE_TIME_FORMATTER else PAYMENT_TIME_FORMATTER
        return formatter.format(zoned)
    }

    /** Centres [text] with leading spaces; truncates if it exceeds [width]. */
    private fun center(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        return " ".repeat((width - text.length) / 2) + text
    }

    /** Lays [left] flush-left and [right] flush-right on one [width]-wide line. */
    private fun twoCol(left: String, right: String, width: Int): String {
        val gap = width - left.length - right.length
        if (gap >= 1) return left + " ".repeat(gap) + right
        val keep = (width - right.length - 1).coerceAtLeast(0)
        return "${left.take(keep)} $right".take(width)
    }

    /** Word-wraps [text] to [width], hard-splitting any token longer than the line. */
    private fun wrap(text: String, width: Int): List<String> {
        if (text.length <= width) return listOf(text)
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (rawWord in text.split(" ").filter { it.isNotEmpty() }) {
            var word = rawWord
            while (word.length > width) {
                flush(current, out)
                out.add(word.take(width))
                word = word.drop(width)
            }
            when {
                word.isEmpty() -> Unit
                current.isEmpty() -> current.append(word)
                current.length + 1 + word.length <= width -> current.append(' ').append(word)
                else -> {
                    flush(current, out)
                    current.append(word)
                }
            }
        }
        flush(current, out)
        return out
    }

    private fun flush(buffer: StringBuilder, out: MutableList<String>) {
        if (buffer.isNotEmpty()) {
            out.add(buffer.toString())
            buffer.clear()
        }
    }

    private companion object {
        const val LABEL_PERIODO = "Periodo"
        const val LABEL_DETALLE_PAGOS = "DETALLE DE PAGOS"
        const val LABEL_METODOS = "POR MÉTODO"
        const val LABEL_RECIBOS = "Recibos"
        const val LABEL_COBRO = "Cobró"
        const val LABEL_NOTAS = "Notas:"
        const val EMPTY_METHODS = "Sin pagos registrados"
        const val SIGNATURE_CAPTION = "Firma de recibido"
        const val CLOSING_TITLE = "FIN DEL REPORTE"
        val COLUMN_WEIGHTS = listOf(3, 1, 2)
        val PAYMENT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val PAYMENT_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(
            "dd/MM HH:mm"
        )
    }
}
