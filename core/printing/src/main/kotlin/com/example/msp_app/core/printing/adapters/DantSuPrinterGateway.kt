package com.example.msp_app.core.printing.adapters

import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.example.msp_app.core.printing.domain.PrintError
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterPort
import com.example.msp_app.core.printing.domain.PrinterProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The Android/DantSu adapter behind [PrinterPort] — the only place Bluetooth and
 * the ESC/POS library are touched. All work runs on [Dispatchers.IO]; every
 * failure is returned as a typed [PrintError] inside a `Result` (never a
 * `Toast`/`Context` — presentation is the UI layer's call), classified by the
 * pure [PrintErrorMapper] seam.
 *
 * **One connection route.** [testConnection] and [print] open the *same*
 * [BluetoothConnection] path ([openConnectedPrinter]); the test simply connects
 * and disconnects, so "it connects in the test" and "it connects when printing"
 * can never diverge.
 *
 * **Reset preamble, verbatim.** Before every job we write the exact three ESC/POS
 * commands the proven msp-app-kt bridge sends — `ESC @` (initialise), `ESC M 0`
 * (font A), `GS ! 0` (character size 1×1) — so the printer never inherits the
 * previous job's font/size/emphasis state. Only after that do we hand text to
 * [EscPosPrinter]. [PrinterProfile.cutAfterPrint] is honoured (default false = no
 * cut, matching msp).
 */
@Singleton
class DantSuPrinterGateway
@Inject
constructor(
    private val discovery: BluetoothPrinterDiscovery
) : PrinterPort {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun listPairedPrinters(): Result<List<PrinterDevice>> =
        runCatchingOnIo { discovery.pairedPrinters() }

    override suspend fun testConnection(device: PrinterDevice): Result<Unit> = runCatchingOnIo {
        val connection = connectWithin(device)
        try {
            // Connect already succeeded above; the disconnect in `finally`
            // below is the entire point of this call.
        } finally {
            // Guarded: a throw from disconnect() must never mask a primary
            // error being propagated out of the `try` above.
            runCatching { connection.disconnect() }
        }
    }

    override suspend fun print(
        device: PrinterDevice,
        ticket: PrintableTicket,
        profile: PrinterProfile
    ): Result<Unit> = runCatchingOnIo {
        val connection = connectWithin(device)
        try {
            // Verbatim reset so this job starts from a known font/size/emphasis
            // state — the msp fix that stops a printer inheriting the last job.
            RESET_PREAMBLE.forEach(connection::write)

            val printer =
                EscPosPrinter(connection, profile.dpi, profile.widthMm, profile.charsPerLine)
            val text = DantSuTicketTranslator.translate(ticket, profile)
            if (profile.cutAfterPrint) {
                printer.printFormattedTextAndCut(text)
            } else {
                printer.printFormattedText(text)
            }
        } finally {
            // Guarded: a throw from disconnect() must never mask the real
            // write/print failure that's already propagating from the try
            // block — a fresh BluetoothConnection is opened per call, so a
            // leaked open socket here would block the very next attempt.
            runCatching { connection.disconnect() }
        }
    }

    /**
     * Bounds the blocking [openConnectedPrinter] with [CONNECT_TIMEOUT_MS] so an
     * absent printer fails fast instead of hanging the whole print job on the OS's
     * ~12 s RFCOMM timeout (the freeze). A plain `withTimeout` cannot interrupt a
     * blocking JVM `connect()`, so the sync connect runs inside [runInterruptible]:
     * a timeout (or a cancel) then interrupts the socket thread and unwinds. The
     * resulting [TimeoutCancellationException] is converted to a typed
     * [PrintError.ConnectionFailed] here — the same error an absent printer already
     * maps to — so it surfaces as a real failure, not a swallowed cancellation.
     */
    private suspend fun connectWithin(device: PrinterDevice): BluetoothConnection = try {
        withTimeout(CONNECT_TIMEOUT_MS) {
            runInterruptible(ioDispatcher) { openConnectedPrinter(device) }
        }
    } catch (timeout: TimeoutCancellationException) {
        throw PrintError.ConnectionFailed(timeout)
    }

    /**
     * The single connection route shared by [print] and [testConnection]. The
     * Bluetooth-on / permission preconditions are the shared [BluetoothPrinterDiscovery.ensureReady]
     * gate; a missing pairing throws [PrintError.NotPaired]; and
     * [BluetoothConnection.connect] throws `EscPosConnectionException` →
     * [PrintError.ConnectionFailed]. Every typed error passes through
     * [PrintErrorMapper] untouched. Stays synchronous — it is always invoked inside
     * [connectWithin]'s [runInterruptible] so its blocking `connect()` is
     * interruptible on timeout/cancel.
     */
    private fun openConnectedPrinter(device: PrinterDevice): BluetoothConnection {
        discovery.ensureReady()
        val bonded = discovery.bondedDevice(device.address) ?: throw PrintError.NotPaired
        return BluetoothConnection(bonded).apply { connect() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> runCatchingOnIo(block: suspend () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.success(block())
            } catch (cancellation: CancellationException) {
                throw cancellation // never swallow cancellation — let a cancelled job unwind
            } catch (throwable: Exception) {
                Result.failure(PrintErrorMapper.map(throwable))
            }
        }

    internal companion object {
        /**
         * Upper bound on the blocking Bluetooth `connect()` (see [connectWithin]).
         * Below the OS's ~12 s RFCOMM timeout so an absent printer fails fast to
         * [PrintError.ConnectionFailed] rather than hanging the print job.
         */
        private const val CONNECT_TIMEOUT_MS = 8_000L

        // The exact msp-app-kt reset sequence, byte-for-byte:
        //   ESC @   (0x1B 0x40) — initialise printer
        //   ESC M 0 (0x1B 0x4D 0x00) — select font A
        //   GS ! 0  (0x1D 0x21 0x00) — character size 1×1 (no double width/height)
        // Locked by DantSuPrinterGatewayTest — do not change these bytes without
        // re-verifying against the proven msp-app-kt bridge.
        internal val RESET_PREAMBLE: List<ByteArray> =
            listOf(
                byteArrayOf(0x1B, 0x40),
                byteArrayOf(0x1B, 0x4D, 0x00),
                byteArrayOf(0x1D, 0x21, 0x00)
            )
    }
}
