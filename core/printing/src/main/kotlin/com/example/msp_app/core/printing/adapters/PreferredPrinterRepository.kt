package com.example.msp_app.core.printing.adapters

import android.content.Context
import androidx.core.content.edit
import com.example.msp_app.core.printing.domain.PreferredPrinterStore
import com.example.msp_app.core.printing.domain.PrinterDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the printer the user last chose, by MAC address, in
 * `SharedPreferences`. Uses the exact same prefs file (`printer_prefs`) and key
 * (`last_printer_address`) as the legacy `:app` picker
 * (`components/selectbluetoothdevice/SelectBluetoothDevice.kt`), so this ported
 * store transparently inherits whatever printer production already saved on the
 * device. The address is only a hint: a printer can be un-paired in Android
 * settings between sessions, so [preferredPrinter] re-validates the saved address
 * against the currently-bonded devices and self-heals (clears the stale entry)
 * when it is gone, so the UI never offers a printer that no longer exists.
 */
@Singleton
class PreferredPrinterRepository
@Inject
constructor(
    @ApplicationContext context: Context
) : PreferredPrinterStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The saved MAC, or null if none was ever chosen. */
    override fun readPreferredAddress(): String? = prefs.getString(KEY_ADDRESS, null)

    /** Persists [address] as the preferred printer. */
    override fun savePreferredAddress(address: String) {
        prefs.edit { putString(KEY_ADDRESS, address) }
    }

    /** Forgets the preferred printer. */
    override fun clear() {
        prefs.edit { remove(KEY_ADDRESS) }
    }

    /**
     * The preferred printer re-validated against [pairedPrinters]. Returns the
     * matching device if it is still paired; otherwise clears the stale saved
     * address and returns null.
     */
    override fun preferredPrinter(pairedPrinters: List<PrinterDevice>): PrinterDevice? {
        val saved = readPreferredAddress() ?: return null
        val match = pairedPrinters.firstOrNull { it.address == saved }
        if (match == null) clear()
        return match
    }

    private companion object {
        const val PREFS = "printer_prefs"
        const val KEY_ADDRESS = "last_printer_address"
    }
}
