package com.example.msp_app.core.printing.domain

/**
 * One logical line of a printable ticket, modelled **agnostic of ESC/POS**.
 * The formatter emits these; the T2 adapter translates them to DantSu markup
 * (only [Bold]/[Header] carry a load-bearing `<b>`; everything else is plain
 * text). Text-bearing lines emitted by the formatter already carry
 * width-baked content (centred / column-laid-out / wrapped for the target
 * profile) — see the formatter's contract. Presentation-only expansion of
 * [Separator]/[Blank]/[ColumnRow] into fixed-width strings is done by
 * [com.example.msp_app.core.printing.application.TicketRenderer].
 */
sealed interface TicketLine {
    /** A centred, emphasised title (e.g. `MUEBLERÍA BONANZA`). Rendered bold. */
    data class Header(val text: String) : TicketLine

    /** A left-aligned plain-text line. */
    data class Line(val text: String) : TicketLine

    /** A left-aligned emphasised line. Rendered bold. */
    data class Bold(val text: String) : TicketLine

    /** A full-width rule filled with [char]. */
    data class Separator(val char: Char = '-') : TicketLine

    /** A blank line. */
    data object Blank : TicketLine

    /**
     * A row of [cols] distributed across the line width according to [weights]
     * (parallel list, same size). Laid out by the renderer: the first column is
     * left-aligned and the last is right-aligned. Reserved for the ticket model
     * (and other ticket types); the payment formatter pre-renders its two-column
     * rows to text per spec §4.
     */
    data class ColumnRow(
        val cols: List<String>,
        val weights: List<Int>
    ) : TicketLine

    /** A centred plain-text line. */
    data class CenteredLine(val text: String) : TicketLine
}

/** A complete ticket: an ordered list of [TicketLine]s. */
typealias PrintableTicket = List<TicketLine>
