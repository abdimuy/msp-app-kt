package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterPort
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.ReportTicket
import javax.inject.Inject

/**
 * Thin orchestration: format a [ReportTicket] for [profile] and hand the
 * ticket to the [PrinterPort]. Sibling to [PrintPaymentReceiptUseCase] —
 * same shape, same "return the port's [Result] verbatim" contract so the
 * `:feature:reportes` ViewModel maps the typed
 * [com.example.msp_app.core.printing.domain.PrintError] to UI state exactly
 * like `ReciboViewModel` already does for payment receipts. Serves both the
 * diario and corte tickets — the caller decides which [ReportTicket] to pass.
 */
class PrintReportTicketUseCase
@Inject
constructor(
    private val port: PrinterPort,
    private val formatter: ReportTicketFormatter
) {
    suspend operator fun invoke(
        device: PrinterDevice,
        ticket: ReportTicket,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): Result<Unit> {
        val printable = formatter.format(ticket, profile)
        return port.print(device, printable, profile)
    }
}
