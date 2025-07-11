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
import androidx.core.content.edit
import com.example.msp_app.core.utils.performPrintRequest
import com.example.msp_app.core.utils.testDeviceConnection
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
    val prefs = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
    val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    val bluetoothAdapter = bluetoothManager?.adapter
    val coroutineScope = rememberCoroutineScope()

    var savedAddress by remember { mutableStateOf<String?>(null) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isPrinting by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var showBluetoothDialog by remember { mutableStateOf(false) }

    val checkBluetoothRef = remember { mutableStateOf<() -> Unit>({}) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = (perms[Manifest.permission.BLUETOOTH_CONNECT] == true
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) &&
                (perms[Manifest.permission.BLUETOOTH_SCAN] == true
                        || Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
        if (granted) {
            checkBluetoothRef.value()
        } else {
            Toast.makeText(context, "Permisos Bluetooth requeridos", Toast.LENGTH_SHORT).show()
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            checkBluetoothRef.value()
        } else {
            Toast.makeText(context, "Bluetooth no habilitado", Toast.LENGTH_SHORT).show()
        }
    }

    checkBluetoothRef.value = fun() {
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
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(intent)
            return
        }
        pairedDevices = bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
        if (pairedDevices.isEmpty()) {
            Toast.makeText(context, "No hay dispositivos Bluetooth emparejados", Toast.LENGTH_SHORT)
                .show()
        }
    }

    LaunchedEffect(Unit) {
        savedAddress = prefs.getString("last_printer_address", null)
        checkBluetoothRef.value()
    }

    LaunchedEffect(pairedDevices) {
        val found = savedAddress
            ?.let { addr -> pairedDevices.firstOrNull { it.address == addr } }
        if (found != null) {
            selectedDevice = found
        } else {
            prefs.edit { remove("last_printer_address") }
            savedAddress = null
        }
    }

    Surface(modifier = modifier) {
        Column {
            Row {
                Button(onClick = {
                    checkBluetoothRef.value()
                    showBluetoothDialog = true
                }) {
                    Text(
                        selectedDevice
                            ?.let { "Imprimir en: ${it.name}" }
                            ?: "Seleccionar impresora"
                    )
                }
                Button(
                    onClick = {
                        selectedDevice?.let { device ->
                            coroutineScope.launch {
                                isPrinting = true
                                performPrintRequest(context, device, textToPrint, onPrintRequest)
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
                        LazyColumn {
                            items(pairedDevices) { device ->
                                Text(
                                    text = device.name ?: "Desconocido",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                if (testDeviceConnection(
                                                        device,
                                                        bluetoothAdapter,
                                                        context
                                                    )
                                                ) {
                                                    prefs.edit {
                                                        putString(
                                                            "last_printer_address",
                                                            device.address
                                                        )
                                                    }
                                                    selectedDevice = device
                                                    showBluetoothDialog = false
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "No se pudo conectar a ${device.name}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                        .padding(8.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
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
