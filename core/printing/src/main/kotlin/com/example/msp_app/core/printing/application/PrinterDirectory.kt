package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PreferredPrinterStore
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterPort
import javax.inject.Inject

/**
 * "Which printers are there, and which one did the collector use last?" — the
 * two questions every print flow asks before sending a ticket, behind one
 * dependency.
 *
 * It exists because [PrinterPort] and [PreferredPrinterStore] are never used
 * apart in a UI flow: the paired list is only ever fetched to re-validate the
 * remembered address against it, and the remembered address is meaningless
 * without that list. Two ports for one question also pushed both ticket
 * ViewModels past detekt's parameter threshold, which was the smell pointing at
 * this.
 *
 * The default is still the last printer used, and "cambiar impresora" is always
 * one tap away — the requirement `CollectionReportViewModel` already honours.
 */
class PrinterDirectory
@Inject
constructor(
    private val port: PrinterPort,
    private val preferred: PreferredPrinterStore
) {
    /** The currently paired printers, or a typed failure. */
    suspend fun emparejadas(): Result<List<PrinterDevice>> = port.listPairedPrinters()

    /**
     * The remembered printer re-validated against [entre]: a device un-paired in
     * Android settings between sessions self-heals to `null` instead of being
     * offered as a target that no longer exists.
     */
    fun recordada(entre: List<PrinterDevice>): PrinterDevice? = preferred.preferredPrinter(entre)

    /** Remembers [device] as the default for next time. */
    fun recordar(device: PrinterDevice) {
        preferred.savePreferredAddress(device.address)
    }
}
