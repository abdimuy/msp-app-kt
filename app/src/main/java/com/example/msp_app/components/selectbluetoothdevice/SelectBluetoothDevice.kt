package com.example.msp_app.components.selectbluetoothdevice

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectBluetoothDevice(
    textToPrint: String,
    modifier: Modifier = Modifier,
    onPrintRequest: suspend (device: BluetoothDevice, text: String) -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)    // nuevo
    var savedAddress by remember { mutableStateOf<String?>(null) }                     // nuevo

    val bluetoothManager = LocalContext.current.getSystemService(
        BluetoothManager::class.java
    )
    val bluetoothAdapter = bluetoothManager?.adapter
    val coroutineScope = rememberCoroutineScope()

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isPrinting by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var showBluetoothDialog by remember { mutableStateOf(false) }

    lateinit var checkBluetoothAndShowDevices: () -> Unit

    fun loadPairedDevices() {
        pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        if (pairedDevices.isEmpty()) {
            Toast.makeText(context, "No hay dispositivos Bluetooth emparejados", Toast.LENGTH_SHORT)
                .show()
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = (permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) &&
                    (permissions[Manifest.permission.BLUETOOTH_SCAN] == true
                            || Build.VERSION.SDK_INT < Build.VERSION_CODES.S)

            if (granted) {
                checkBluetoothAndShowDevices()
            } else {
                Toast.makeText(context, "Permisos Bluetooth requeridos", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val launcherEnableBluetooth = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                loadPairedDevices()
            } else {
                Toast.makeText(context, "Bluetooth no habilitado", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun checkBluetoothAndShowDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Dispositivo no soporta Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val hasScan = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasConnect || !hasScan) {
                permissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
                return
            }
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            launcherEnableBluetooth.launch(enableBtIntent)
        } else {
            loadPairedDevices()
        }
    }

    LaunchedEffect(Unit) {
        savedAddress = prefs.getString("last_printer_address", null)  // cargar última
        checkBluetoothAndShowDevices()
    }

    LaunchedEffect(pairedDevices) {
        val deviceFound = savedAddress?.let { addr ->
            pairedDevices.firstOrNull { it.address == addr }
        }
        if (deviceFound != null) {
            selectedDevice = deviceFound
        } else {
            // Impresora guardada no disponible: eliminar preferencia
            prefs.edit().remove("last_printer_address").apply()
            savedAddress = null
        }
    }

    Surface(modifier = modifier) {
        Column {
            LaunchedEffect(Unit) {
                checkBluetoothAndShowDevices()
            }

            Row {
                Button(
                    onClick = {
                        showBluetoothDialog = true
                    },
                ) {
                    if (selectedDevice != null) {
                        Text("Imprimir en: ${selectedDevice?.name ?: "Selecciona dispositivo"}")
                    } else {
                        Text("Seleccionar impresora")
                    }
                }
                Button(
                    onClick = {
                        selectedDevice?.let { device ->
                            isPrinting = true
                            coroutineScope.launch {
                                try {
                                    onPrintRequest(device, textToPrint)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Error al imprimir: ${e.message}",
                                        Toast.LENGTH_LONG
                                    )
                                        .show()
                                }
                                isPrinting = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp)
                ) {
                    Text(if (isPrinting) "Imprimiendo..." else "Imprimir")
                }
            }

            if (showBluetoothDialog) {
                AlertDialog(
                    onDismissRequest = { showBluetoothDialog = false },
                    title = { Text("Selecciona impresora Bluetooth") },
                    text = {
                        if (pairedDevices.isEmpty()) {
                            Text("No se encontraron dispositivos emparejados")
                        } else {
                            LazyColumn {
                                items(pairedDevices) { device ->
                                    Text(
                                        text = device.name ?: "Dispositivo desconocido",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                prefs.edit()                                                        // guardar
                                                    .putString(
                                                        "last_printer_address",
                                                        device.address
                                                    )
                                                    .apply()
                                                selectedDevice = device
                                                showBluetoothDialog = false
                                            }
                                            .padding(8.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showBluetoothDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
}
