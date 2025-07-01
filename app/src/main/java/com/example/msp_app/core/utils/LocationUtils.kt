package com.example.msp_app.core.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Helper para solicitar permisos de ubicación, verificar ajustes de GPS y obtener la ubicación puntual.
 * Maneja llamadas concurrentes serializando las peticiones con un Mutex.
 *
 * Uso en Composable:
 * ```kotlin
 * val activity = LocalActivity.current as? ComponentActivity ?: error("... required")
 * val provider = remember { LocationProvider(activity) }
 * val launcher = rememberLauncherForActivityResult(
 *     ActivityResultContracts.RequestPermission()
 * ) { granted ->
 *     provider.onPermissionResult(granted)
 * }
 * LaunchedEffect(Unit) {
 *   try {
 *     val loc = provider.requestLocation(launcher)
 *     // usa loc.latitude / loc.longitude
 *   } catch (e: ResolvableApiException) {
 *     // GPS apagado: e.startResolutionForResult(...)
 *   } catch (e: SecurityException) {
 *     // permiso denegado
 *   } catch (e: Throwable) {
 *     // otros errores
 *   }
 * }
 * ```
 */
class LocationProvider(private val activity: ComponentActivity) {

    // Serializa peticiones concurrentes
    private val requestMutex = Mutex()

    // WeakReference a la continuación de la petición de permiso
    private var permissionContinuation: WeakReference<Continuation<Boolean>>? = null

    /**
     * Debe invocarse desde el callback del launcher de permisos:
     */
    fun onPermissionResult(granted: Boolean) {
        permissionContinuation?.get()?.resume(granted)
        permissionContinuation = null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Verifica que el GPS/ajustes de ubicación estén habilitados.
     * Lanza ResolvableApiException si es necesario resolver.
     */
    private suspend fun ensureLocationSettings() {
        suspendCancellableCoroutine<Unit> { cont ->
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            ).build()
            val settingsReq = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true)
                .build()
            val client = LocationServices.getSettingsClient(activity)
            client.checkLocationSettings(settingsReq)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { exc -> cont.resumeWithException(exc) }
            cont.invokeOnCancellation { /* no-op */ }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(activity)
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
            cont.invokeOnCancellation { cts.cancel() }
        }

    /**
     * Solicita permiso si hace falta, verifica GPS y devuelve la ubicación.
     * Serializa peticiones concurrentes.
     * Lanza SecurityException si el permiso es denegado.
     * Lanza ResolvableApiException si los ajustes de ubicación requieren resolución.
     *
     * @param launcher ActivityResultLauncher<String> configurado con RequestPermission()
     */
    suspend fun requestLocation(launcher: ActivityResultLauncher<String>): Location? =
        requestMutex.withLock {
            if (!hasLocationPermission()) {
                val granted = suspendCoroutine<Boolean> { cont ->
                    permissionContinuation = WeakReference(cont)
                    launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (!granted) throw SecurityException("Permiso de ubicación denegado")
            }
            ensureLocationSettings()
            return getCurrentLocation()
        }
}
