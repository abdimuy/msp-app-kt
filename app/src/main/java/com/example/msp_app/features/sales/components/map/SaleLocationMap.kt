package com.example.msp_app.features.sales.components.map

import android.Manifest
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.msp_app.BuildConfig
import com.example.msp_app.core.utils.LocationTracker
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class GoogleGeocodingResponse(
    val results: List<GoogleGeocodingResult>,
    val status: String
)

data class GoogleGeocodingResult(
    val address_components: List<AddressComponent>,
    val formatted_address: String,
    val geometry: GoogleGeometry
)

data class AddressComponent(
    val long_name: String,
    val short_name: String,
    val types: List<String>
)

data class GoogleGeometry(
    val location: GoogleLatLng
)

data class GoogleLatLng(
    val lat: Double,
    val lng: Double
)

interface GoogleGeocodingApi {
    @GET("maps/api/geocode/json")
    suspend fun reverseGeocode(
        @Query("latlng") latlng: String,
        @Query("key") apiKey: String,
        @Query("language") language: String = "es",
        @Query("region") region: String = "mx"
    ): GoogleGeocodingResponse
}

sealed class GeocodingResult {
    data class Success(val address: String) : GeocodingResult()
    data class Error(val message: String) : GeocodingResult()
    object Loading : GeocodingResult()
}

class HybridGeocodingService(private val context: android.content.Context) {
    private val googleApi: GoogleGeocodingApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleGeocodingApi::class.java)
    }

    suspend fun getAddressFromLocation(lat: Double, lng: Double): GeocodingResult {
        return try {
            if (BuildConfig.MAPS_API_KEY.isBlank()) {
                return GeocodingResult.Error("API Key no configurada")
            }

            val address = getAddressFromGoogle(lat, lng)
            GeocodingResult.Success(address)
        } catch (e: HttpException) {
            when (e.code()) {
                403 -> GeocodingResult.Error("API Key inválida o sin permisos")
                429 -> GeocodingResult.Error("Límite de solicitudes excedido")
                else -> GeocodingResult.Error("Error del servidor: ${e.code()}")
            }
        } catch (e: Exception) {
            GeocodingResult.Error("Error de conexión: ${e.localizedMessage}")
        }
    }

    private suspend fun getAddressFromGoogle(lat: Double, lng: Double): String {
        val response = googleApi.reverseGeocode(
            latlng = "$lat,$lng",
            apiKey = BuildConfig.MAPS_API_KEY
        )

        return when (response.status) {
            "OK" -> {
                if (response.results.isNotEmpty()) {
                    formatMexicanAddress(response.results.first())
                } else {
                    throw Exception("Sin resultados de geocodificación")
                }
            }

            "ZERO_RESULTS" -> "No se encontró dirección para esta ubicación"
            "OVER_QUERY_LIMIT" -> throw Exception("Límite de consultas excedido")
            "REQUEST_DENIED" -> throw Exception("Solicitud denegada - revisar API Key")
            "INVALID_REQUEST" -> throw Exception("Solicitud inválida")
            else -> throw Exception("Error desconocido: ${response.status}")
        }
    }

    private fun formatMexicanAddress(result: GoogleGeocodingResult): String {
        var streetNumber = ""
        var route = ""
        var neighborhood = ""
        var locality = ""
        var adminArea = ""

        result.address_components.forEach { component ->
            when {
                component.types.contains("street_number") -> {
                    streetNumber = component.long_name
                }

                component.types.contains("route") -> {
                    route = component.long_name
                }

                component.types.contains("sublocality_level_1") ||
                        component.types.contains("neighborhood") -> {
                    neighborhood = component.long_name
                }

                component.types.contains("locality") -> {
                    locality = component.long_name
                }

                component.types.contains("administrative_area_level_1") -> {
                    adminArea = component.long_name
                }
            }
        }

        val addressParts = mutableListOf<String>()

        if (route.isNotEmpty()) {
            if (streetNumber.isNotEmpty()) {
                addressParts.add("$route $streetNumber")
            } else {
                addressParts.add(route)
            }
        }

        if (neighborhood.isNotEmpty()) {
            addressParts.add("Col. $neighborhood")
        }

        if (locality.isNotEmpty()) {
            addressParts.add(locality)
        }

        if (adminArea.isNotEmpty()) {
            addressParts.add(adminArea)
        }

        return if (addressParts.isNotEmpty()) {
            addressParts.joinToString(", ")
        } else {
            result.formatted_address
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationMap(
    onAddressChange: (String) -> Unit,
    onLocationChange: (Location) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var geocodingResult by remember { mutableStateOf<GeocodingResult?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }

    val geocodingService = remember { HybridGeocodingService(context) }
    val scope = rememberCoroutineScope()

    var geocodingJob by remember { mutableStateOf<Job?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(19.4326, -99.1332),
            17f
        )
    }

    fun requestAddressForLocation(location: Location) {
        geocodingJob?.cancel()
        geocodingJob = scope.launch {
            delay(500)
            geocodingResult = GeocodingResult.Loading

            val result = geocodingService.getAddressFromLocation(
                location.latitude,
                location.longitude
            )

            geocodingResult = result

            when (result) {
                is GeocodingResult.Success -> {
                    onAddressChange(result.address)
                }

                is GeocodingResult.Error -> {
                    onAddressChange("Error: ${result.message}")
                }

                else -> {}
            }
        }
    }

    fun recenterMap() {
        currentLocation?.let { location ->
            scope.launch {
                cameraPositionState.animate(
                    update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        17f
                    ),
                    durationMs = 1000
                )
            }
        }
    }

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            isLoadingLocation = true
            locationError = null

            try {
                LocationTracker(context).locationUpdates().collect { location ->
                    val isFirstLocation = currentLocation == null
                    currentLocation = location
                    onLocationChange(location)

                    if (isFirstLocation) {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(
                            LatLng(location.latitude, location.longitude), 17f
                        )
                    }

                    isLoadingLocation = false
                    requestAddressForLocation(location)
                }
            } catch (e: Exception) {
                locationError = "Error al obtener ubicación: ${e.localizedMessage ?: e.message}"
                isLoadingLocation = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            geocodingJob?.cancel()
        }
    }

    Column(modifier = modifier) {
        if (currentLocation != null || geocodingResult != null) {
            AddressCard(
                geocodingResult = geocodingResult,
                currentLocation = currentLocation,
                onRecenterClick = ::recenterMap
            )
        }

        MapCard(
            permissionState = permissionState,
            cameraPositionState = cameraPositionState,
            currentLocation = currentLocation,
            isLoadingLocation = isLoadingLocation,
            locationError = locationError,
            geocodingResult = geocodingResult
        )
    }
}

@Composable
private fun AddressCard(
    geocodingResult: GeocodingResult?,
    currentLocation: Location?,
    onRecenterClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (geocodingResult) {
                is GeocodingResult.Loading -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                is GeocodingResult.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Dirección:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (currentLocation != null) {
                    IconButton(
                        onClick = onRecenterClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Recentrar mapa",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            when (geocodingResult) {
                is GeocodingResult.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Obteniendo dirección...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is GeocodingResult.Success -> {
                    Text(
                        geocodingResult.address,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                is GeocodingResult.Error -> {
                    Text(
                        geocodingResult.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                null -> {
                    Text(
                        "Ubicación no disponible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun MapCard(
    permissionState: com.google.accompanist.permissions.PermissionState,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    currentLocation: Location?,
    isLoadingLocation: Boolean,
    locationError: String?,
    geocodingResult: GeocodingResult?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            if (permissionState.status.isGranted) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = true,
                        mapType = MapType.NORMAL
                    ),
                    uiSettings = MapUiSettings(
                        myLocationButtonEnabled = false,
                        zoomControlsEnabled = true,
                        compassEnabled = true,
                        rotationGesturesEnabled = true,
                        scrollGesturesEnabled = true,
                        tiltGesturesEnabled = true,
                        zoomGesturesEnabled = true
                    )
                ) {
                    currentLocation?.let { loc ->
                        val snippet = when (geocodingResult) {
                            is GeocodingResult.Success -> geocodingResult.address
                            is GeocodingResult.Loading -> "Obteniendo dirección..."
                            is GeocodingResult.Error -> "Dirección no disponible"
                            null -> "Aquí te encuentras"
                        }

                        Marker(
                            state = MarkerState(
                                position = LatLng(loc.latitude, loc.longitude)
                            ),
                            title = "Ubicación actual",
                            snippet = snippet
                        )
                    }
                }
            } else {
                PermissionRequestContent(
                    onRequestPermission = { permissionState.launchPermissionRequest() }
                )
            }

            if (isLoadingLocation) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            locationError?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Se requieren permisos de ubicación",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onRequestPermission) {
            Text("Conceder permisos")
        }
    }
}