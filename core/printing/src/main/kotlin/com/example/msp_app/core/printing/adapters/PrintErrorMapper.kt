package com.example.msp_app.core.printing.adapters

import com.dantsu.escposprinter.exceptions.EscPosBarcodeException
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.exceptions.EscPosEncodingException
import com.dantsu.escposprinter.exceptions.EscPosParserException
import com.example.msp_app.core.printing.domain.PrintError
import java.io.IOException

/**
 * The single, hardware-free seam that translates whatever the Bluetooth/DantSu
 * stack throws into a typed [PrintError]. Kept pure (a `Throwable -> PrintError`
 * function, no Android, no I/O) so the whole failure taxonomy is unit-testable
 * without a printer: the [DantSuPrinterGateway]'s one catch block funnels every
 * throwable through here.
 *
 * Precondition failures the gateway detects itself — Bluetooth off, device not
 * paired, permission missing — are thrown as [PrintError] instances and pass
 * straight through. Everything the connect/print call throws is classified by
 * type: connection-phase problems become [PrintError.ConnectionFailed], and
 * content-phase problems (parser/encoding/barcode) become
 * [PrintError.WriteFailed]. A missing runtime `BLUETOOTH_CONNECT` surfaces from
 * the platform as a [SecurityException] → [PrintError.PermissionDenied]. Anything
 * unrecognised is [PrintError.Unknown], preserving the cause for diagnostics.
 */
object PrintErrorMapper {
    fun map(throwable: Throwable): PrintError = when (throwable) {
        // A precondition the gateway already classified — do not re-wrap it.
        is PrintError -> throwable
        is SecurityException -> PrintError.PermissionDenied
        // Note: DantSu raises the same EscPosConnectionException type whether the
        // failure happened during the initial connect() or while flushing bytes
        // mid-print (write phase) — the exception carries no phase marker to tell
        // them apart. We intentionally bucket it as ConnectionFailed (not
        // WriteFailed) in both cases rather than guess.
        is EscPosConnectionException -> PrintError.ConnectionFailed(throwable)
        is EscPosParserException,
        is EscPosEncodingException,
        is EscPosBarcodeException
        -> PrintError.WriteFailed(throwable)
        is IOException -> PrintError.ConnectionFailed(throwable)
        else -> PrintError.Unknown(throwable)
    }
}
