package com.example.msp_app.core.printing.application

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.printing.domain.PrintLogStore
import com.example.msp_app.core.printing.domain.PrintRecord
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterPort
import com.example.msp_app.core.printing.domain.PrinterProfile
import java.time.Instant
import javax.inject.Inject

/**
 * Reads the print permission of one ticket: the day-of-collection rule
 * ([PrintDayRule]) evaluated against what the durable [PrintLogStore] already
 * knows about it.
 *
 * This is what a SCREEN uses to paint itself — to turn the CTA off and to say
 * whether the next paper would be a copy. It is **not** what makes the rule
 * true: that is [PrintTicketUseCase], which re-evaluates at the moment of
 * printing. A verdict read to paint a button is stale the instant it is read.
 */
class EvaluatePrintPermission
@Inject
constructor(
    private val log: PrintLogStore,
    private val clock: AppClock
) {
    operator fun invoke(ticketId: String, cobradoEn: Instant): PrintPermission =
        PrintDayRule.evaluar(
            cobradoEn = cobradoEn,
            ahora = clock.now(),
            registro = log.find(ticketId)
        )
}

/**
 * Prints a ticket. **The choke point where both halves of the rule are made
 * true**, in this order:
 *
 * 1. **It re-evaluates the day of collection against a FRESH clock read**, and
 *    refuses with [ImpresionFueraDelDia] without touching the printer.
 * 2. It prints.
 * 3. It registers the print — same call, so there is no path that prints
 *    without logging.
 *
 * ## Why the day check lives here and not in the callers
 *
 * The first version checked the rule only when the screen loaded and then
 * printed against that stored verdict. A screen opened at 23:58 and tapped at
 * 00:01 — or a ViewModel that survived an overnight backgrounding — printed on a
 * day that is not the collection day, which is the one rule this whole feature
 * exists to enforce. The button being painted correctly is not the rule; the
 * clock read at the moment of printing is.
 *
 * Putting it at the choke point is the same argument that already makes
 * print-and-log indivisible: a third ticket type added later cannot forget it,
 * because there is no way to reach [PrinterPort] from a feature without coming
 * through here.
 *
 * The log is written **only on success** — a ticket that never reached the
 * printer is not a print, and counting it would make the next real print look
 * like a reprint on a paper the customer never got. A refusal is likewise not a
 * print: nothing is logged.
 *
 * The failure of the port is returned verbatim (typed
 * [com.example.msp_app.core.printing.domain.PrintError]) so the caller maps it
 * to es-MX UI state exactly like `CollectionReportViewModel` already does.
 */
class PrintTicketUseCase
@Inject
constructor(
    private val port: PrinterPort,
    private val log: PrintLogStore,
    private val clock: AppClock
) {
    /**
     * @param cobradoEn when the money was taken (or the visit registered) — the
     *   input of the day rule. It is a parameter and not something the caller
     *   has already checked precisely so that it CANNOT be skipped.
     */
    suspend operator fun invoke(
        device: PrinterDevice,
        ticketId: String,
        cobradoEn: Instant,
        ticket: PrintableTicket,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): Result<PrintRecord> {
        // Lectura FRESCA del reloj, aquí y no en la pantalla. Ver el KDoc.
        val permiso = PrintDayRule.evaluar(
            cobradoEn = cobradoEn,
            ahora = clock.now(),
            registro = log.find(ticketId)
        )
        if (permiso == PrintPermission.FueraDelDia) {
            return Result.failure(ImpresionFueraDelDia)
        }
        return port.print(device, ticket, profile)
            .map { log.record(ticketId, clock.now()) }
    }
}
