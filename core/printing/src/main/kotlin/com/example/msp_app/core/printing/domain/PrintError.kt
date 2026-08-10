package com.example.msp_app.core.printing.domain

/**
 * A typed printing failure. The adapter returns these inside a [Result.failure]
 * (never a `Toast`/`Context` — the domain and adapter stay UI-free per spec §3);
 * the UI layer decides how to present each case. Exhaustive so the ViewModel's
 * `when` covers every failure path.
 */
sealed class PrintError : Exception() {
    /** Bluetooth is turned off on the device. */
    data object BluetoothDisabled : PrintError()

    /** The target printer is not among the paired devices. */
    data object NotPaired : PrintError()

    /** Could not open a connection to the printer. */
    data class ConnectionFailed(override val cause: Throwable? = null) : PrintError()

    /** The connection opened but writing the ticket failed. */
    data class WriteFailed(override val cause: Throwable? = null) : PrintError()

    /** A required runtime Bluetooth permission was not granted. */
    data object PermissionDenied : PrintError()

    /** An unclassified failure. */
    data class Unknown(override val cause: Throwable? = null) : PrintError()
}
