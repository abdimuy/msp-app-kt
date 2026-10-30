package com.example.msp_app.core.ubicacion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.msp_app.core.common.location.captura.AccesoAUbicacion
import com.example.msp_app.core.common.location.captura.FixDeUbicacion
import com.example.msp_app.core.common.location.captura.ProveedorDeUbicacion
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implementación real con Fused (E-APP-142): `lastLocation` y actualizaciones de alta precisión
 * mientras alguien escuche. Al cancelarse el flujo quita la petición (`awaitClose`). No usa
 * foreground service ni ACTIVITY_RECOGNITION.
 */
class ProveedorDeUbicacionFused(private val context: Context) : ProveedorDeUbicacion {

    override fun acceso(): AccesoAUbicacion {
        val permiso = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!permiso) return AccesoAUbicacion.SIN_PERMISO
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return if (lm != null && LocationManagerCompat.isLocationEnabled(lm)) {
            AccesoAUbicacion.OK
        } else {
            AccesoAUbicacion.GPS_APAGADO
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun ultimaConocida(): FixDeUbicacion? =
        LocationServices.getFusedLocationProviderClient(context).lastLocation.await()?.aFix()

    @SuppressLint("MissingPermission")
    override fun actualizaciones(): Flow<FixDeUbicacion> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVALO_MS)
            .setMinUpdateIntervalMillis(INTERVALO_MINIMO_MS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.aFix()) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private companion object {
        const val INTERVALO_MS = 5_000L
        const val INTERVALO_MINIMO_MS = 2_000L
    }
}

internal fun Location.aFix(): FixDeUbicacion = FixDeUbicacion(
    lat = latitude,
    lon = longitude,
    precisionM = if (hasAccuracy()) accuracy.toDouble() else Double.NaN,
    hora = Instant.ofEpochMilli(time),
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    velocidadMs = if (hasSpeed()) speed.toDouble() else null,
    esMock = isMock
)
