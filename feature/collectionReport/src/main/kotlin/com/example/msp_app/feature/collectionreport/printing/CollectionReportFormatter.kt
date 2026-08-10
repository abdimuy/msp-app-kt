package com.example.msp_app.feature.collectionreport.printing

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.printing.application.TicketRenderer
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.TicketLine
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.PaymentRowUi

/**
 * Mapea el estado del reporte de cobranza ([CollectionReportUiState]) al **contenido del
 * ticket** que se imprime en la impresora térmica (P2): una [PrintableTicket] semántica
 * ([TicketLine]) ancho-consciente, reusando el seam de formato de `:core:printing`
 * ([TicketRenderer] para el texto plano, [PrinterProfile] para el ancho) — mismo patrón que
 * `ReportTicketFormatter`/`PaymentReceiptFormatter` del módulo (helpers de layout
 * `center`/`twoCol`/`wrap` duplicados a propósito por convención del módulo: cada formatter es
 * una unidad autocontenida y testeable de forma independiente).
 *
 * **Dinero SIEMPRE en peso entero vía [formatMoneyMxn]** (nunca `Double`, nunca
 * `Double.toCurrency`) — el `Money`/`BigDecimal` de escala 2 sigue exacto por dentro; el
 * redondeo a peso entero (decisión de negocio: MSP no opera con centavos) ocurre solo en el
 * borde de display, igual que el tablero.
 *
 * **Una sola fuente de verdad del contenido:** la impresión térmica real
 * (`CollectionReportViewModel.printReport` -> `PrinterPort.print`), el PDF y "Compartir"
 * (`ReportActionsController`) consumen TODOS este mismo mapeo — la impresión toma
 * [toTicketLines] (y el `PrinterPort`/`DantSuTicketTranslator` aplica el accent-fold central
 * de `:core:printing` justo antes del codepage de la impresora, "aplicado una vez,
 * centralmente" por diseño del módulo); el PDF/Compartir toman [toTicketText], el MISMO
 * contenido/layout/dinero renderizado a texto plano — con acentos conservados, porque esos
 * canales (pantalla/WhatsApp) sí los muestran y el mockup los usa. Misma fuente, codificación
 * apropiada a cada dispositivo.
 */
@Suppress("TooManyFunctions")
object CollectionReportFormatter {

    private const val MAX_CLIENT_CHARS = 24
    private const val LINE_SEPARATOR = "\n"
    private const val LABEL_DETALLE = "DETALLE DE PAGOS"
    private const val LABEL_TOTAL = "Total cobrado"
    private const val LABEL_EFECTIVO = "Efectivo"
    private const val LABEL_TRANSFERENCIA = "Transferencia"
    private const val LABEL_CONDONADO = "Condonado"
    private const val LABEL_VISITAS = "Visitas"
    private const val LABEL_GENERADO = "Generado"

    /**
     * El ticket semántico ([TicketLine]) ancho-consciente para [profile] — lo que se
     * imprime (vía `PrinterPort.print`, que le agrega el `<b>` de énfasis y el accent-fold)
     * y la fuente de [toTicketText] (PDF/Compartir). Encabezado + cobrador + rango -> detalle
     * por pago (solo periodo Día, [DetailUi.Payments]) -> totales (Total/Efectivo/
     * Transferencia + Condonado/Visitas cuando existen) -> "Generado <fecha y hora>".
     */
    fun toTicketLines(
        state: CollectionReportUiState,
        clock: AppClock = AppClock.System,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): PrintableTicket {
        val width = profile.charsPerLine
        return buildList {
            add(TicketLine.Header(center(reportTitle(state.period).uppercase(), width)))
            add(TicketLine.CenteredLine(center(state.cobrador, width)))
            wrap(
                state.rangeLabel,
                width
            ).forEach { add(TicketLine.CenteredLine(center(it, width))) }
            add(TicketLine.Separator())
            addPaymentsBlock(state, width)
            addTotalsBlock(state, width)
            add(TicketLine.Separator())
            add(TicketLine.CenteredLine(center("$LABEL_GENERADO ${printedAtLabel(clock)}", width)))
        }
    }

    /**
     * El ticket como texto plano ancho-consciente para el PDF y "Compartir" — el MISMO
     * contenido/layout/dinero que se imprime (mismo [toTicketLines] + [TicketRenderer]),
     * con acentos conservados (canales de pantalla). El accent-fold del codepage de la
     * impresora lo aplica solo el `PrinterPort` en la ruta de impresión real.
     */
    fun toTicketText(
        state: CollectionReportUiState,
        clock: AppClock = AppClock.System,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): String = TicketRenderer.render(toTicketLines(state, clock, profile), profile)
        .joinToString(LINE_SEPARATOR)

    /** Título del reporte según el periodo — fuente única (lo reusa `ReportActionsController`). */
    fun reportTitle(period: ReportPeriod): String = when (period) {
        ReportPeriod.DIA -> "Reporte de cobranza del día"
        ReportPeriod.SEMANA -> "Reporte de cobranza del ciclo"
    }

    /**
     * Bloque "DETALLE DE PAGOS": solo cuando el estado conserva los pagos individuales
     * (periodo Día, [DetailUi.Payments]); en Semana ([DetailUi.Days]) no hay desglose y el
     * bloque se omite entero (encabezado + separador incluidos), mismo criterio que el sheet.
     */
    private fun MutableList<TicketLine>.addPaymentsBlock(
        state: CollectionReportUiState,
        width: Int
    ) {
        val payments = (state.detail as? DetailUi.Payments)?.rows.orEmpty()
        if (payments.isEmpty()) return
        add(TicketLine.Bold(center(LABEL_DETALLE, width)))
        payments.forEach { row -> addPaymentLine(row, width) }
        add(TicketLine.Separator())
    }

    /**
     * Una entrada de pago en dos líneas (mismo layout que el ticket de producción viejo):
     * hora + cliente (truncado a [MAX_CLIENT_CHARS]) arriba, y la forma de cobro a la
     * izquierda con el monto alineado a la derecha abajo.
     */
    private fun MutableList<TicketLine>.addPaymentLine(row: PaymentRowUi, width: Int) {
        val hora = AppTime.formatForDisplay(row.paidAt, AppTime.Formats.TIME_24H)
        add(TicketLine.Line("$hora ${row.cliente.take(MAX_CLIENT_CHARS)}"))
        add(TicketLine.Line(twoCol("  ${row.method.ticketLabel()}", money(row.amount), width)))
    }

    /**
     * Bloque de totales: Total cobrado + Efectivo/Transferencia con su conteo; Condonado y
     * Visitas solo cuando el estado los trae (nunca se inventa un cero — se omite la línea).
     */
    private fun MutableList<TicketLine>.addTotalsBlock(state: CollectionReportUiState, width: Int) {
        add(TicketLine.Line(twoCol(LABEL_TOTAL, money(state.hero.monto), width)))
        add(
            TicketLine.Line(
                twoCol(
                    "$LABEL_EFECTIVO (${state.efectivo.count})",
                    money(state.efectivo.amount),
                    width
                )
            )
        )
        add(
            TicketLine.Line(
                twoCol(
                    "$LABEL_TRANSFERENCIA (${state.transferencia.count})",
                    money(state.transferencia.amount),
                    width
                )
            )
        )
        state.condonado.amount?.let {
            add(
                TicketLine.Line(twoCol(LABEL_CONDONADO, money(it), width))
            )
        }
        state.visitas.count?.let {
            add(TicketLine.Line(twoCol(LABEL_VISITAS, it.toString(), width)))
        }
    }

    /** "Generado el" — fecha y hora locales del dispositivo al momento de generar el ticket. */
    fun printedAtLabel(clock: AppClock, pattern: String = AppTime.Formats.DATE_TIME_24H): String =
        AppTime.formatForDisplay(clock.now(), pattern)

    private fun money(amount: Money): String = formatMoneyMxn(amount.amount)

    private fun PaymentMethod.ticketLabel(): String = when (this) {
        PaymentMethod.EFECTIVO -> "Efectivo"
        PaymentMethod.TRANSFERENCIA -> "Transfer."
        PaymentMethod.CHEQUE -> "Cheque"
        PaymentMethod.CONDONACION -> "Condonado"
        PaymentMethod.OTRO -> "Otro"
    }

    /** Centra [text] con espacios a la izquierda; trunca si excede [width]. */
    private fun center(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        return " ".repeat((width - text.length) / 2) + text
    }

    /** Coloca [left] pegado a la izquierda y [right] pegado a la derecha en una línea de [width]. */
    private fun twoCol(left: String, right: String, width: Int): String {
        val gap = width - left.length - right.length
        if (gap >= 1) return left + " ".repeat(gap) + right
        val keep = (width - right.length - 1).coerceAtLeast(0)
        return "${left.take(keep)} $right".take(width)
    }

    /** Envuelve [text] a [width]; parte en duro cualquier token más largo que la línea. */
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
}
