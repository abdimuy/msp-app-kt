package com.example.msp_app.core.printing.domain

/**
 * The hexagonal port to a thermal printer. DantSu and Bluetooth live entirely
 * behind this interface in the T2 adapter; the domain and application layers
 * depend only on this abstraction. Every method returns a [Result] carrying a
 * typed [PrintError] on failure — there is a single connection route
 * ("test = print"), so [testConnection] exercises the same path as [print].
 */
interface PrinterPort {
    /** Lists the currently paired printers, or a [PrintError] on failure. */
    suspend fun listPairedPrinters(): Result<List<PrinterDevice>>

    /** Opens (and closes) a connection to [device] to verify it is reachable. */
    suspend fun testConnection(device: PrinterDevice): Result<Unit>

    /** Prints [ticket] to [device] using [profile], or a [PrintError] on failure. */
    suspend fun print(
        device: PrinterDevice,
        ticket: PrintableTicket,
        profile: PrinterProfile
    ): Result<Unit>
}
