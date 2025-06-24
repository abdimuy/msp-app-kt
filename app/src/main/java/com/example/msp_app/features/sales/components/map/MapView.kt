package com.example.msp_app.features.sales.components.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.tasks.await

@Composable
fun MapView(
    modifier: Modifier = Modifier,
    context: Context,
    initialZoom: Float = 16f,
) {
    val location = rememberLocation(context)
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(location.value) {
        location.value?.let {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(it, initialZoom)
            )
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = location.value != null
        ),
    ) {
        location.value?.let {
            Marker(state = MarkerState(position = it), title = "Tu ubicación")
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun rememberLocation(context: Context): State<LatLng?> {
    val locationState = remember { mutableStateOf<LatLng?>(null) }
    var permissionGranted by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!permissionGranted) {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            val fusedLocation = LocationServices.getFusedLocationProviderClient(context)
            try {
                val location = fusedLocation.lastLocation.await()
                location?.let {
                    locationState.value = LatLng(it.latitude, it.longitude)
                }
            } catch (e: Exception) {
                Log.e("MapError", "${e.localizedMessage}")
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permiso de Ubicación Necesario") },
            text = { Text("Esta aplicación necesita el permiso de ubicación para funcionar correctamente. Por favor, activa el permiso desde la configuración de la aplicación.") },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    // Abrir la configuración de la aplicación
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", context.packageName, null)
                    intent.data = uri
                    context.startActivity(intent)
                }) {
                    Text("Abrir Configuración")
                }
            },
            dismissButton = {
                Button(onClick = { showPermissionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    return locationState
}
