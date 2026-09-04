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
 * Injected by both `:feature:pagos` and `:feature:visitas` so the rule and the
 * ledger are consulted the same way from both tickets — the clock is
 * `AppClock`, the only date source, and never `Instant.now()`.
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
 * Prints an already-formatted ticket and **registers the print in the same
 * call**.
 *
 * This pairing is the whole point of the class: "cada impresión queda
 * registrada" is only true if there is no code path that prints without
 * logging. Two call sites doing `port.print(...)` and then remembering to log
 * would be two chances to forget, and the second one would ship a reprint that
 * looks like a first copy.
 *
 * The log is written **only on success** — a ticket that never reached the
 * printer is not a print, and counting it would make the next real print look
 * like a reprint on a paper the customer never got.
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
    suspend operator fun invoke(
        device: PrinterDevice,
        ticketId: String,
        ticket: PrintableTicket,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): Result<PrintRecord> = port.print(device, ticket, profile)
        .map { log.record(ticketId, clock.now()) }
}
