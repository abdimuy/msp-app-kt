package com.example.msp_app.core.utils

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThermalPrinting {
    suspend fun printText(
        device: BluetoothDevice,
        text: String,
        context: Context,
        dpi: Int = 203,
        widthMm: Float = 48f,
        charactersPerLine: Int = 32
    ) {
        withContext(Dispatchers.IO) {
            try {
                val connection = BluetoothConnection(device)
                connection.connect()

                connection.write(byteArrayOf(0x1B, 0x40))
                connection.write(byteArrayOf(0x1B, 0x4D, 0x00))
                connection.write(byteArrayOf(0x1D, 0x21, 0x00))

                val printer = EscPosPrinter(connection, dpi, widthMm, charactersPerLine)
                printer.printFormattedText(text)

                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al imprimir: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    fun centerText(text: String, width: Int): String {
        val padding = (width - text.length) / 2
        return " ".repeat(padding.coerceAtLeast(0)) + text
    }

    fun bold(text: String): String = "<b>$text</b>"
}