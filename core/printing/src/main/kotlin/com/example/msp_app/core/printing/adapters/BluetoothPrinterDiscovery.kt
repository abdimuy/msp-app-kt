package com.example.msp_app.core.printing.adapters

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.msp_app.core.printing.domain.PrintError
import com.example.msp_app.core.printing.domain.PrinterDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enumerates the already-paired ("bonded") Bluetooth printers and centralises the
 * runtime-permission gate, mirroring the optional-hardware pattern of
 * `feature/abono/PhotoCapture.kt` (gate → check → request). v1 never scans or
 * pairs in-app (spec §6): the user pairs the printer in Android settings, so this
 * only reads `bondedDevices`.
 *
 * On API 31+ reading bonded devices or their names requires the runtime
 * `BLUETOOTH_CONNECT` permission; below 31 it is manifest-only (always granted at
 * runtime). This class exposes the check and the permission list the picker UI
 * (T5) will hand to its `RequestMultiplePermissions` launcher — it never launches
 * a request itself (no `Activity`/UI here). The enumeration throws typed
 * [PrintError]s so the gateway can surface them uniformly.
 */
// A cohesive Bluetooth-permission-gate + bonded-device facade: its functions are
// all single-purpose (support/enabled/permission checks + enumeration) and belong
// together — splitting them across types would fragment one concern, so the
// detekt TooManyFunctions threshold is scoped-suppressed here (mirrors AppTime).
@Suppress("TooManyFunctions")
@Singleton
class BluetoothPrinterDiscovery
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    private val adapter by lazy {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    }

    /** True on devices that expose a Bluetooth radio at all. */
    fun isBluetoothSupported(): Boolean = adapter != null

    /** True when Bluetooth is present and switched on. */
    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * Whether the runtime permission needed to talk to a paired printer is held.
     * Always true below API 31, where the legacy manifest permissions suffice.
     */
    fun hasConnectPermission(): Boolean = hasRuntimePermission(
        Manifest.permission.BLUETOOTH_CONNECT
    )

    /**
     * Whether the runtime scan permission is held. On API 31+ printing needs it
     * because DantSu's `BluetoothConnection.connect()` internally calls
     * `BluetoothAdapter.cancelDiscovery()`, which raises a `SecurityException`
     * without `BLUETOOTH_SCAN`. Always true below API 31 (manifest-only).
     */
    fun hasScanPermission(): Boolean = hasRuntimePermission(Manifest.permission.BLUETOOTH_SCAN)

    /** True below API 31 (manifest-only); otherwise checks the granted runtime [permission]. */
    private fun hasRuntimePermission(permission: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /**
     * The runtime permissions the picker UI must request before enumerating —
     * empty below API 31 (manifest-only), `BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN`
     * from 31 onward (SCAN is needed by DantSu's `cancelDiscovery()` on connect).
     */
    fun requiredRuntimePermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            emptyList()
        }

    /** Paired printers as domain value objects, for the port / picker UI. */
    fun pairedPrinters(): List<PrinterDevice> =
        bondedDevices().map { PrinterDevice(address = it.address, name = it.deviceName()) }

    /** The bonded platform device with [address], or null if it is no longer paired. */
    fun bondedDevice(address: String): BluetoothDevice? =
        bondedDevices().firstOrNull { it.address == address }

    /**
     * Asserts Bluetooth is on and the runtime permission is held, throwing the
     * matching typed [PrintError] otherwise. The gateway reuses this so print and
     * enumeration share one precondition gate (rather than a raw
     * `SecurityException`/NPE surfacing).
     */
    fun ensureReady() {
        val error =
            when {
                adapter?.isEnabled != true -> PrintError.BluetoothDisabled
                !hasConnectPermission() -> PrintError.PermissionDenied
                !hasScanPermission() -> PrintError.PermissionDenied
                else -> null
            }
        if (error != null) throw error
    }

    /** The raw bonded devices, guarded by [ensureReady] so every failure is typed. */
    @SuppressLint("MissingPermission")
    private fun bondedDevices(): List<BluetoothDevice> {
        ensureReady()
        return adapter?.bondedDevices?.toList().orEmpty()
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.deviceName(): String = name ?: address
}
