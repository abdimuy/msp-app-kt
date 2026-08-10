package com.example.msp_app.core.printing.domain

/**
 * A Bluetooth thermal printer the app can print to, identified by its MAC
 * [address]. A pure value object — deliberately free of `android.bluetooth.*`
 * so the domain and formatter stay unit-testable without Android. The T2
 * adapter maps this to/from the platform `BluetoothDevice`.
 *
 * @property address the printer's Bluetooth MAC (e.g. `"00:11:22:33:44:55"`);
 *   the stable identity used to remember the preferred printer.
 * @property name the human-readable device name shown in the picker.
 */
data class PrinterDevice(
    val address: String,
    val name: String
)
