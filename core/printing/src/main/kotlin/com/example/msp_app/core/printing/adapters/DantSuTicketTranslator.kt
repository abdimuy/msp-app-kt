package com.example.msp_app.core.printing.adapters

import com.example.msp_app.core.printing.application.TicketRenderer
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.TicketLine

/**
 * Pure bridge from a semantic [PrintableTicket] to the single formatted string
 * DantSu's `printFormattedText` consumes. Layout (centring, columns, separators,
 * wrapping) is already baked into fixed-width text by the formatter + width-aware
 * [TicketRenderer]; the only DantSu markup that is load-bearing here is `<b>`,
 * which we wrap around the emphasised lines ([TicketLine.Header] and
 * [TicketLine.Bold]) so the printer bolds them. Everything else prints as its
 * plain rendered text, and lines are joined with `\n` — mirroring the proven
 * msp-app-kt bridge (manual padding + `<b>`, no alignment tags).
 *
 * Kept free of Android and DantSu runtime types so the emphasis logic is
 * unit-testable without hardware.
 *
 * **ASCII-folded, centrally.** [foldToPrintableAscii] runs once here, on the
 * final joined string, so every ticket — report and payment receipt alike —
 * is stripped of accents before it reaches the printer's limited codepage.
 * This is the last pure seam before Android/DantSu types take over in
 * [DantSuPrinterGateway], and it is the one seam both formatters share.
 */
object DantSuTicketTranslator {
    private const val LINE_SEPARATOR = "\n"

    fun translate(ticket: PrintableTicket, profile: PrinterProfile): String {
        val rendered = TicketRenderer.render(ticket, profile)
        val joined =
            ticket
                .zip(rendered) { line, text -> if (line.isEmphasised()) "<b>$text</b>" else text }
                .joinToString(LINE_SEPARATOR)
        return foldToPrintableAscii(joined)
    }

    private fun TicketLine.isEmphasised(): Boolean =
        this is TicketLine.Header || this is TicketLine.Bold
}
