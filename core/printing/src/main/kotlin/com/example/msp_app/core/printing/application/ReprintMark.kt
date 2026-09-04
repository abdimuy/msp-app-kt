package com.example.msp_app.core.printing.application

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.printing.domain.TicketLine

/**
 * The mark that makes a reprint **detectable on the paper itself**.
 *
 * Registering the print in a store the collector cannot see would only make a
 * reprint detectable to a developer with a debugger. The customer, the office
 * and whoever audits the route all hold paper, so the copy says on its face that
 * it is a copy, which copy it is, and at what time the first one came out.
 *
 * Shared by the payment ticket and the visit ticket so the two cannot disagree
 * about what a reprint looks like.
 */
object ReprintMark {

    private const val TITULO = "*** REIMPRESION ***"
    private const val ETIQUETA_COPIA = "copia"
    private const val ETIQUETA_PRIMERA = "primera"

    /**
     * The banner lines for [permiso], or an empty list when the ticket is a
     * first print (nothing to warn about) or cannot print at all.
     *
     * The copy number is `previas + 1`: two earlier prints make this the third
     * copy. The first-print time is rendered in the business zone, the same zone
     * the day rule uses, so the hour on the paper and the day the rule allowed
     * can never come from two different clocks.
     *
     * Every literal here is plain ASCII on purpose: [foldToPrintableAscii]
     * DROPS a codepoint it cannot render (a middle dot, an em dash), and a
     * dropped character would shift a line the layout had already centred.
     */
    fun lines(permiso: PrintPermission, width: Int): List<TicketLine> {
        val reimpresion = permiso as? PrintPermission.Reimpresion ?: return emptyList()
        val copia = reimpresion.previas + 1
        val hora = AppTime.formatForDisplay(reimpresion.primeraVez, AppTime.Formats.TIME_24H)
        return listOf(
            TicketLine.Separator('='),
            TicketLine.Bold(TicketLayout.center(TITULO, width)),
            TicketLine.CenteredLine(
                TicketLayout.center("$ETIQUETA_COPIA $copia - $ETIQUETA_PRIMERA $hora", width)
            ),
            TicketLine.Separator('=')
        )
    }
}
