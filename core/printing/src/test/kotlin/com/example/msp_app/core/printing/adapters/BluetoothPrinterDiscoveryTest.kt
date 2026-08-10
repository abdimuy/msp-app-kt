package com.example.msp_app.core.printing.adapters

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric coverage of the runtime-permission gate. The load-bearing assertion:
 * on API 31+ the picker must request BOTH `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`.
 * SCAN is required because DantSu's `BluetoothConnection.connect()` internally calls
 * `BluetoothAdapter.cancelDiscovery()`; requesting CONNECT alone caused the
 * "falta el permiso de Bluetooth" retry loop on the A25 (Android 15).
 */
@RunWith(RobolectricTestRunner::class)
class BluetoothPrinterDiscoveryTest {
    private val discovery = BluetoothPrinterDiscovery(ApplicationProvider.getApplicationContext())

    @Test
    @Config(sdk = [34], application = Application::class)
    fun `requiredRuntimePermissions requests CONNECT and SCAN on API 31+`() {
        val perms = discovery.requiredRuntimePermissions()

        assertTrue(
            "must request BLUETOOTH_CONNECT",
            perms.contains(Manifest.permission.BLUETOOTH_CONNECT)
        )
        assertTrue(
            "must request BLUETOOTH_SCAN",
            perms.contains(Manifest.permission.BLUETOOTH_SCAN)
        )
    }

    @Test
    @Config(sdk = [30], application = Application::class)
    fun `requiredRuntimePermissions is empty below API 31 (manifest-only)`() {
        assertEquals(emptyList<String>(), discovery.requiredRuntimePermissions())
    }
}
