package com.example.msp_app.feature.collectionreport.printing

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.printing.application.TicketRenderer
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.TicketLine
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.DetailSort
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.PaymentRowUi
import com.example.msp_app.feature.collectionreport.ui.VisitRowUi

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

    private const val LINE_SEPARATOR = "\n"
    private const val LABEL_DETALLE = "DETALLE DE PAGOS"
    private const val LABEL_DETALLE_VISITAS = "DETALLE DE VISITAS"
    private const val LABEL_TOTAL = "Total cobrado"
    private const val LABEL_EFECTIVO = "Efectivo"
    private const val LABEL_TRANSFERENCIA = "Transferencia"
    private const val LABEL_CONDONADO = "Condonado"
    private const val LABEL_VISITAS = "Visitas"
    private const val LABEL_GENERADO = "Generado"

    /**
     * Prefijo de cada línea de pago/visita en periodo Semana: solo la fecha (el ciclo cruza
     * varios días, y a diferencia de Día la hora exacta ya no es el dato relevante para
     * ubicar la fila — Task 4).
     */
    private const val PREFIX_DATE = "dd/MM"

    /**
     * El ticket semántico ([TicketLine]) ancho-consciente para [profile] — lo que se
     * imprime (vía `PrinterPort.print`, que le agrega el `<b>` de énfasis y el accent-fold)
     * y la fuente de [toTicketText] (PDF/Compartir). Encabezado + cobrador + rango -> detalle
     * por pago (TODOS los pagos, en ambos periodos) -> detalle de visitas (cuando el estado
     * las trae) -> totales (Total/Efectivo/Transferencia + Condonado/Visitas cuando existen)
     * -> "Generado <fecha y hora>".
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
            addVisitsBlock(state, width)
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
        ReportPeriod.SEMANA -> "Reporte de cobranza de la semana"
    }

    /**
     * Bloque "DETALLE DE PAGOS": TODOS los pagos del rango, en AMBOS periodos — Día lee
     * [DetailUi.Payments] directo; Semana ([DetailUi.Days]) no conserva la lista plana en
     * `detail`, así que se aplana `state.dayPayments` (pagos individuales por día del ciclo,
     * ya en orden cronológico). `when` exhaustivo sobre el sealed [DetailUi]: si se agrega un
     * tercer caso, este bloque deja de compilar en vez de omitirlo en silencio.
     *
     * El orden impreso sigue [CollectionReportUiState.sort] (Task 4, [sortedPayments]) — el
     * toggle Hora/Fecha·Nombre del tablero ([com.example.msp_app.feature.collectionreport.ui.components.DetailHeader])
     * también gobierna lo que se imprime, en AMBOS periodos.
     */
    private fun MutableList<TicketLine>.addPaymentsBlock(
        state: CollectionReportUiState,
        width: Int
    ) {
        val payments = when (val detail = state.detail) {
            is DetailUi.Payments -> detail.rows
            is DetailUi.Days -> state.dayPayments.flatten()
        }
        if (payments.isEmpty()) return
        add(TicketLine.Bold(center(LABEL_DETALLE, width)))
        sortedPayments(payments, state.sort)
            .forEach { row -> addPaymentLine(row, state.period, width) }
        add(TicketLine.Separator())
    }

    /** Orden del detalle impreso: cronológico ([DetailSort.HORA]) o alfabético por cliente. */
    private fun sortedPayments(payments: List<PaymentRowUi>, sort: DetailSort): List<PaymentRowUi> =
        when (sort) {
            DetailSort.HORA -> payments.sortedBy { it.paidAt }
            DetailSort.NOMBRE -> payments.sortedBy { it.cliente.lowercase() }
        }

    /**
     * Una entrada de pago en UNA sola línea (58mm = 32 chars — Task 1: el ticket viejo de dos
     * líneas por pago desperdiciaba papel en tickets largos, y la forma de cobro por fila ya
     * no aporta nada que el bloque de totales no diga): [prefix] (hora en Día; solo fecha
     * `dd/MM` en Semana, que cruza varios días) + cliente a la izquierda, truncado para que la
     * línea quepa exacto en [width] junto con el monto — que va alineado a la derecha vía
     * [twoCol]. La forma de cobro YA NO se repite por fila (sigue viviendo en
     * [addTotalsBlock]).
     */
    private fun MutableList<TicketLine>.addPaymentLine(
        row: PaymentRowUi,
        period: ReportPeriod,
        width: Int
    ) {
        val prefix = when (period) {
            ReportPeriod.DIA -> AppTime.formatForDisplay(row.paidAt, AppTime.Formats.TIME_24H)
            ReportPeriod.SEMANA -> AppTime.formatForDisplay(row.paidAt, PREFIX_DATE)
        }
        val amount = money(row.amount)
        val maxClient = (width - prefix.length - 1 - amount.length - 1).coerceAtLeast(0)
        val left = "$prefix ${row.cliente.take(maxClient)}"
        add(TicketLine.Line(twoCol(left, amount, width)))
    }

    /**
     * Bloque "DETALLE DE VISITAS": una línea por visita con hora/fecha + cliente, más el TIPO
     * y la nota completa del cobrador (ver [addVisitLines]). Se omite entero (encabezado +
     * separador incluidos) cuando `state.visitRows` viene vacío — nunca se inventa una línea
     * "Visitas 0" con desglose. Se muestra en AMBOS periodos (Día y Semana): la línea
     * totalizadora "Visitas N" de [addTotalsBlock] no desaparece, este bloque solo la
     * complementa con el detalle.
     */
    private fun MutableList<TicketLine>.addVisitsBlock(state: CollectionReportUiState, width: Int) {
        val visits = state.visitRows
        if (visits.isEmpty()) return
        add(TicketLine.Bold(center(LABEL_DETALLE_VISITAS, width)))
        visits.forEach { visit -> addVisitLines(visit, state.period, width) }
        add(TicketLine.Separator())
    }

    /**
     * Las líneas de UNA visita (Task 2 — antes el ticket solo mostraba cliente + nota, sin el
     * TIPO elegido al capturarla, p. ej. "No se encontraba" / "Pidió que regrese otro día"):
     * línea 1 = [prefix] (hora en Día, fecha `dd/MM` en Semana — mismo lenguaje visual que
     * [addPaymentLine]) + cliente; línea 2 = el TIPO de visita, cuando viene poblado; líneas
     * siguientes = la nota COMPLETA envuelta e indentada. Las visitas son la EXCEPCIÓN a la
     * regla de una línea por fila de [addPaymentLine]: no hay un monto que alinear a la
     * derecha, y la nota (texto libre del cobrador) es el dato accionable que nunca se trunca.
     */
    private fun MutableList<TicketLine>.addVisitLines(
        visit: VisitRowUi,
        period: ReportPeriod,
        width: Int
    ) {
        val prefix = when (period) {
            ReportPeriod.DIA -> AppTime.formatForDisplay(visit.visitedAt, AppTime.Formats.TIME_24H)
            ReportPeriod.SEMANA -> AppTime.formatForDisplay(visit.visitedAt, PREFIX_DATE)
        }
        val maxClient = (width - prefix.length - 1).coerceAtLeast(0)
        add(TicketLine.Line("$prefix ${visit.cliente.take(maxClient)}"))
        if (visit.tipo.isNotBlank()) {
            add(TicketLine.Line("  ${visit.tipo}".take(width)))
        }
        if (visit.nota.isNotBlank()) {
            wrap("  ${visit.nota}", width).forEach { add(TicketLine.Line(it)) }
        }
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
