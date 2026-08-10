package com.example.msp_app.core.printing.domain

/**
 * Port for remembering the printer the user last chose, across sessions. The
 * Android/`SharedPreferences` implementation
 * ([com.example.msp_app.core.printing.adapters.PreferredPrinterRepository]) lives
 * behind this interface so the T4 ViewModel depends only on the abstraction and
 * stays unit-testable with a plain in-memory fake (spec §3 — no Android in the
 * layers the ViewModel talks to for its state machine).
 *
 * The saved address is only a hint: a printer can be un-paired in Android
 * settings between sessions, so [preferredPrinter] re-validates the saved
 * address against the currently-bonded devices and self-heals — see the adapter.
 */
interface PreferredPrinterStore {
    /** The saved MAC, or null if none was ever chosen. */
    fun readPreferredAddress(): String?

    /** Persists [address] as the preferred printer. */
    fun savePreferredAddress(address: String)

    /** Forgets the preferred printer. */
    fun clear()

    /**
     * The preferred printer re-validated against [pairedPrinters]: the matching
     * still-paired device, or null (clearing any stale saved address).
     */
    fun preferredPrinter(pairedPrinters: List<PrinterDevice>): PrinterDevice?
}
