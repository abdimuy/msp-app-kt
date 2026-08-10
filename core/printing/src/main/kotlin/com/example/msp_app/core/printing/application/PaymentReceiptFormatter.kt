package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PaymentReceipt
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.ReceiptProductLine
import com.example.msp_app.core.printing.domain.TicketLine
import javax.inject.Inject

/**
 * Turns a [PaymentReceipt] into a width-aware [PrintableTicket]. **Pure** — no
 * Android, no I/O, no money arithmetic (amounts arrive pre-formatted, spec §3).
 * Everything the printer width affects is decided here from
 * [PrinterProfile.charsPerLine]: centred titles, two-column rows and long-name
 * wrapping are baked into each emitted line's text so the T2 adapter only has to
 * add `<b>` for bold lines. Separators/blanks stay semantic and are expanded by
 * [TicketRenderer] at print time.
 *
 * Section order (spec mockup): business + branch → RECIBO DE PAGO → folio + date
 * → customer (name, address, phone) + credit → **PRODUCTOS** + Total a crédito
 * (+ Enganche, when known and positive) → **ABONO** (bold) + method → saldo
 * detail (anterior / abono / actual / pagado,
 * each omitted when null) + disclaimer → **ULTIMOS PAGOS** history → collector →
 * thanks + phones. Optional blocks (products, saldo, history) are omitted whole —
 * header and surrounding separator included — when their data is empty/null, so
 * the ticket never prints an empty heading or a stray rule. There is no signature
 * block (removed per the spec mockup).
 *
 * **Long-text policy:** narrative [TicketLine.Line] content (the customer name,
 * the concept) is **word-wrapped** to the line width — a receipt the customer
 * keeps must never silently drop their name; an oversized single token is
 * hard-split. Fixed structural fields (centred titles, column values such as
 * money/method) are truncated to the width, which in practice they never reach.
 */
// The width-aware layout is split into small, single-purpose private section
// builders (productos/saldo/history) plus the shared text helpers
// (center/twoCol/threeCol/wrap) for readability; collapsing them would produce
// one unreadable mega-method, so the detekt TooManyFunctions threshold is
// scoped-suppressed here (mirrors AppTime).
@Suppress("TooManyFunctions")
class PaymentReceiptFormatter
@Inject
constructor() {
    fun format(receipt: PaymentReceipt, profile: PrinterProfile): PrintableTicket {
        val width = profile.charsPerLine
        return buildList {
            add(TicketLine.Header(center(receipt.negocio, width)))
            if (receipt.sucursal.isNotBlank()) {
                add(TicketLine.CenteredLine(center(receipt.sucursal, width)))
            }
            add(TicketLine.Separator())

            add(TicketLine.Header(center(TITLE, width)))
            add(TicketLine.Line(twoCol(LABEL_FOLIO, receipt.folio, width)))
            add(TicketLine.Line(twoCol(LABEL_FECHA, receipt.fechaHora, width)))
            add(TicketLine.Separator())

            wrap("$LABEL_CLIENTE ${receipt.cliente}", width).forEach { add(TicketLine.Line(it)) }
            receipt.domicilio?.takeIf { it.isNotBlank() }?.let { dom ->
                wrap("$LABEL_DOMICILIO $dom", width).forEach { add(TicketLine.Line(it)) }
            }
            receipt.telefonoCliente?.takeIf { it.isNotBlank() }?.let { tel ->
                add(TicketLine.Line("$LABEL_TELEFONO $tel"))
            }
            add(TicketLine.Line(twoCol(LABEL_CREDITO, receipt.credito, width)))
            // Concepto is a fallback description only when the sale has no synced
            // product lines — a full PRODUCTOS block supersedes it (spec mockup).
            if (receipt.productos.isEmpty() && receipt.concepto.isNotBlank()) {
                wrap(receipt.concepto, width).forEach { add(TicketLine.Line(it)) }
            }
            add(TicketLine.Separator())

            addProductosBlock(receipt, width)

            add(TicketLine.Bold(twoCol(LABEL_ABONO, receipt.abono, width)))
            add(TicketLine.Line(twoCol(LABEL_PAGO, receipt.metodo, width)))
            add(TicketLine.Separator())

            addSaldoBlock(receipt, width)
            addHistoryBlock(receipt, width)

            add(TicketLine.Line(twoCol(LABEL_COBRO, receipt.cobrador, width)))
            add(TicketLine.Separator())

            add(TicketLine.Header(center(THANKS, width)))
            if (receipt.telefonos.isNotBlank()) {
                wrap(
                    receipt.telefonos,
                    width
                ).forEach { add(TicketLine.CenteredLine(center(it, width))) }
            }
        }
    }

    /**
     * The `PRODUCTOS` + `Total a crédito` (+ optional `Enganche`) region (each
     * closed by a separator). Omitted whole when there are neither product lines
     * nor a total; when products are absent but a total is known, only the total
     * (+ enganche) lines are printed (spec edge case).
     */
    private fun MutableList<TicketLine>.addProductosBlock(receipt: PaymentReceipt, width: Int) {
        if (receipt.productos.isNotEmpty()) {
            add(TicketLine.Bold(LABEL_PRODUCTOS))
            receipt.productos.forEach { producto -> addProductLines(producto, width) }
            receipt.precioTotal?.let { total ->
                add(TicketLine.Separator())
                add(TicketLine.Line(twoCol(LABEL_PRECIO_TOTAL, total, width)))
                addEngancheLine(receipt, width)
            }
            add(TicketLine.Separator())
        } else if (receipt.precioTotal != null) {
            add(TicketLine.Line(twoCol(LABEL_PRECIO_TOTAL, receipt.precioTotal, width)))
            addEngancheLine(receipt, width)
            add(TicketLine.Separator())
        }
    }

    /** The `Enganche` line, right after the total — omitted when `null` (mapper-gated to `> 0`). */
    private fun MutableList<TicketLine>.addEngancheLine(receipt: PaymentReceipt, width: Int) {
        receipt.enganche?.let { enganche ->
            add(
                TicketLine.Line(twoCol(LABEL_ENGANCHE, enganche, width))
            )
        }
    }

    /**
     * The saldo detail (anterior → abono subtraction → actual → pagado a la fecha),
     * each line omitted when its value is null. The disclaimer + closing separator
     * appear only when at least one saldo line was printed.
     */
    private fun MutableList<TicketLine>.addSaldoBlock(receipt: PaymentReceipt, width: Int) {
        var sawSaldoLine = false
        receipt.saldoAnterior?.let { anterior ->
            add(TicketLine.Line(twoCol(LABEL_SALDO_ANTERIOR, anterior, width)))
            add(TicketLine.Line(twoCol(LABEL_ABONO_RESTA, "- ${receipt.abono}", width)))
            sawSaldoLine = true
        }
        receipt.saldoActual?.let { actual ->
            add(TicketLine.Bold(twoCol(LABEL_SALDO_ACTUAL, actual, width)))
            sawSaldoLine = true
        }
        receipt.pagadoALaFecha?.let { pagado ->
            add(TicketLine.Line(twoCol(LABEL_PAGADO, pagado, width)))
            sawSaldoLine = true
        }
        if (sawSaldoLine) {
            add(TicketLine.CenteredLine(center(SALDO_DISCLAIMER, width)))
            add(TicketLine.Separator())
        }
    }

    /** The `ULTIMOS PAGOS` history block (closed by a separator); omitted when empty. */
    private fun MutableList<TicketLine>.addHistoryBlock(receipt: PaymentReceipt, width: Int) {
        if (receipt.ultimosPagos.isEmpty()) return
        add(TicketLine.Bold(LABEL_ULTIMOS_PAGOS))
        receipt.ultimosPagos.forEach { pago ->
            add(TicketLine.Line(threeCol(pago.fecha, pago.monto, pago.metodo, width)))
        }
        add(TicketLine.Separator())
    }

    /**
     * Renders one product: its wrapped description with the quantity right-aligned
     * on the first line (kept on its own line when the description fills the width),
     * then the pre-formatted line total right-aligned on the next line — matching
     * the spec mockup.
     */
    private fun MutableList<TicketLine>.addProductLines(producto: ReceiptProductLine, width: Int) {
        val qty = "x${producto.cantidad}"
        val descLines = wrap(producto.descripcion, width)
        val qtyFitsOnFirst = descLines.first().length + 1 + qty.length <= width
        descLines.forEachIndexed { index, line ->
            if (index == 0 && qtyFitsOnFirst) {
                add(TicketLine.Line(twoCol(line, qty, width)))
            } else {
                add(TicketLine.Line(line))
            }
        }
        if (!qtyFitsOnFirst) {
            add(TicketLine.Line(twoCol("", qty, width)))
        }
        add(TicketLine.Line(twoCol("", producto.importe, width)))
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

    /**
     * Lays three columns on one [width]-wide line: [left] flush-left, [right]
     * flush-right, [mid] centred in the remaining span. Baked to text (like
     * [twoCol]) so the golden test asserts final strings. On the rare overlap of
     * long values the right column wins (it is written last); the history values
     * this serves are short and never collide.
     */
    private fun threeCol(left: String, mid: String, right: String, width: Int): String {
        val cells = CharArray(width) { ' ' }
        fun place(text: String, start: Int) {
            val from = start.coerceIn(0, width)
            text.take(width - from).forEachIndexed { i, c -> cells[from + i] = c }
        }
        place(left.take(width), 0)
        val m = mid.take(width)
        place(m, (width - m.length) / 2)
        val r = right.take(width)
        place(r, width - r.length)
        return String(cells)
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
        const val TITLE = "RECIBO DE PAGO"
        const val THANKS = "¡Gracias por su pago!"
        const val SALDO_DISCLAIMER = "* saldo sujeto a confirmación"
        const val LABEL_FOLIO = "Folio"
        const val LABEL_FECHA = "Fecha"
        const val LABEL_CLIENTE = "Cliente:"
        const val LABEL_DOMICILIO = "Dom:"
        const val LABEL_TELEFONO = "Tel:"
        const val LABEL_CREDITO = "Crédito"
        const val LABEL_PRODUCTOS = "PRODUCTOS"
        const val LABEL_PRECIO_TOTAL = "Total a crédito"
        const val LABEL_ENGANCHE = "Enganche"
        const val LABEL_ABONO = "ABONO"
        const val LABEL_PAGO = "Pago"
        const val LABEL_SALDO_ANTERIOR = "Saldo anterior"
        const val LABEL_ABONO_RESTA = "Abono"
        const val LABEL_SALDO_ACTUAL = "Saldo actual"
        const val LABEL_PAGADO = "Pagado a la fecha"
        const val LABEL_ULTIMOS_PAGOS = "ULTIMOS PAGOS"
        const val LABEL_COBRO = "Cobró"
    }
}
