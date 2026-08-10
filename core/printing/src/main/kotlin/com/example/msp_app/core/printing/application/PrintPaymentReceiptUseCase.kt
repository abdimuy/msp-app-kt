package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PaymentReceipt
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterPort
import com.example.msp_app.core.printing.domain.PrinterProfile
import javax.inject.Inject

/**
 * Thin orchestration: format a [PaymentReceipt] for [profile] and hand the
 * ticket to the [PrinterPort]. Returns the port's [Result] verbatim so callers
 * (the T4 ViewModel) can map the typed
 * [com.example.msp_app.core.printing.domain.PrintError] to UI state.
 */
class PrintPaymentReceiptUseCase
@Inject
constructor(
    private val port: PrinterPort,
    private val formatter: PaymentReceiptFormatter
) {
    suspend operator fun invoke(
        device: PrinterDevice,
        receipt: PaymentReceipt,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): Result<Unit> {
        val ticket = formatter.format(receipt, profile)
        return port.print(device, ticket, profile)
    }
}
