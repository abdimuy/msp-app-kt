package com.example.msp_app.core.printing.adapters

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.printing.domain.PrinterDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric coverage of the SharedPreferences-backed preferred-printer store:
 * persist a MAC, read it back, and self-heal when the saved printer is no longer
 * among the bonded devices. Mirrors `ThemePreferencesTest` (plain [Application]
 * so no unrelated DI graph is built).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PreferredPrinterRepositoryTest {
    private val repo = PreferredPrinterRepository(ApplicationProvider.getApplicationContext())

    private val printer = PrinterDevice(address = "00:11:22:33:44:55", name = "PT-210")
    private val otherPrinter = PrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "PT-58")

    @Test
    fun `readPreferredAddress is null before any save`() {
        assertNull(repo.readPreferredAddress())
    }

    @Test
    fun `save then read round-trips the MAC`() {
        repo.savePreferredAddress(printer.address)

        assertEquals(printer.address, repo.readPreferredAddress())
    }

    @Test
    fun `clear forgets the saved MAC`() {
        repo.savePreferredAddress(printer.address)

        repo.clear()

        assertNull(repo.readPreferredAddress())
    }

    @Test
    fun `preferredPrinter returns the device when still paired`() {
        repo.savePreferredAddress(printer.address)

        assertEquals(printer, repo.preferredPrinter(listOf(otherPrinter, printer)))
    }

    @Test
    fun `preferredPrinter clears and returns null when the saved printer is unpaired`() {
        repo.savePreferredAddress(printer.address)

        // The saved printer is no longer among the currently-bonded devices.
        assertNull(repo.preferredPrinter(listOf(otherPrinter)))
        assertNull(repo.readPreferredAddress())
    }

    @Test
    fun `preferredPrinter returns null when nothing was saved`() {
        assertNull(repo.preferredPrinter(listOf(printer)))
    }
}
