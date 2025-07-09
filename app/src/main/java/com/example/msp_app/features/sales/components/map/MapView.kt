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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class MapPin(
    val lat: Double,
    val lon: Double,
    val description: String,
)

@Composable
fun MapView(
    modifier: Modifier = Modifier,
    context: Context,
    pins: List<MapPin> = emptyList(),
    initialZoom: Float = 13f,
    cameraPositionState: CameraPositionState
) {
    val location = rememberLocation(context)

    LaunchedEffect(pins) {
        if (pins.isNotEmpty()) {
            val focusLatLng = getClusterCenter(pins)
            cameraPositionState.position = CameraPosition.fromLatLngZoom(focusLatLng, initialZoom)
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = location.value != null,
        ),
    ) {
        pins.forEach { pin ->
            Marker(
                state = MarkerState(position = LatLng(pin.lat, pin.lon)),
                title = pin.description
            )
        }
    }
}

private fun getClusterCenter(pins: List<MapPin>, radiusMeters: Double = 300.0): LatLng {
    var maxCount = 0
    var bestCenter: LatLng = LatLng(pins[0].lat, pins[0].lon)
    for (pin in pins) {
        val center = LatLng(pin.lat, pin.lon)
        val count = pins.count {
            haversine(pin.lat, pin.lon, it.lat, it.lon) <= radiusMeters
        }
        if (count > maxCount) {
            maxCount = count
            bestCenter = center
        }
    }
    return bestCenter
}

private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371000.0 // Radio de la Tierra en metros
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
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

    DisposableEffect(permissionGranted) {
        var callback: LocationCallback? = null
        if (permissionGranted) {
            val fusedLocation = LocationServices.getFusedLocationProviderClient(context)
            val locationRequest = LocationRequest.Builder(
                3000 // intervalo en ms
            )
                .setMinUpdateIntervalMillis(2000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()
            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation
                    if (loc != null) {
                        locationState.value = LatLng(loc.latitude, loc.longitude)
                    }
                }
            }
            try {
                fusedLocation.requestLocationUpdates(
                    locationRequest,
                    callback,
                    android.os.Looper.getMainLooper()
                )
            } catch (e: Exception) {
                Log.e("MapError", "${e.localizedMessage}")
            }
        }
        onDispose {
            if (permissionGranted && callback != null) {
                val fusedLocation = LocationServices.getFusedLocationProviderClient(context)
                fusedLocation.removeLocationUpdates(callback)
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
