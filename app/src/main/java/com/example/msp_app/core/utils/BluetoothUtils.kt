package com.example.msp_app.core.utils

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

suspend fun performPrintRequest(
    context: Context,
    device: BluetoothDevice,
    text: String,
    onPrintRequest: suspend (BluetoothDevice, String) -> Unit
) {
    try {
        withContext(Dispatchers.IO) {
            onPrintRequest(device, text)
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error al imprimir: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

suspend fun testDeviceConnection(
    device: BluetoothDevice,
    adapter: BluetoothAdapter?,
    context: Context
): Boolean = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED
    ) return@withContext false

    return@withContext try {
        val socket = device.createRfcommSocketToServiceRecord(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        )
        adapter?.cancelDiscovery()
        socket.connect()
        socket.close()
        true
    } catch (_: Exception) {
        false
    }
}

