package com.example.msp_app.data.visitas

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/**
 * Implementación real de [UbicacionPort] sobre Play Services.
 *
 * **No arranca `UpdateLocationService`.** Ese servicio es un defecto conocido
 * del plan (§8.1: revienta con `SecurityException` sin `try/catch`) y además es
 * el que antes sostenía el encolado de las visitas (§8.2, cerrado por la Task
 * 5). Colgar de él el camino nuevo reintroduciría exactamente el acoplamiento
 * que esa tarea quitó. Aquí se pide la ubicación de una vez, y si no llega, no
 * llega: el servicio sigue parchando `LAT`/`LNG` después por su cuenta.
 *
 * **Contesta `null`, no lanza**, cuando el permiso está negado — la comprobación
 * es explícita y previa, así que la `SecurityException` no es siquiera el camino
 * normal. El caso de uso reporta ese `null` con su código de telemetría y
 * registra la visita igual.
 */
class UbicacionDeVisitaAdapter(
    private val context: Context
) : UbicacionPort {

    override suspend fun ubicacionActual(): UbicacionDeLaVisita? {
        if (!tienePermiso()) return null
        val cliente = LocationServices.getFusedLocationProviderClient(context)
        val cancelacion = CancellationTokenSource()
        return try {
            cliente
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancelacion.token)
                .await()
                ?.let { UbicacionDeLaVisita(lat = it.latitude, lng = it.longitude) }
        } finally {
            // Sin esto, una corrutina cancelada dejaría viva la petición al
            // proveedor: el `finally` corre también en la cancelación.
            cancelacion.cancel()
        }
    }

    private fun tienePermiso(): Boolean = PERMISOS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        /** Cualquiera de los dos basta: la visita no necesita precisión fina. */
        val PERMISOS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
