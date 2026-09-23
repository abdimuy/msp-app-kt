package com.example.msp_app.core.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * **Una sola lectura de dónde está el cobrador, para ordenar una lista.**
 *
 * Es el hermano de un tiro de [LocationTracker], que entrega un *flujo*. Los dos
 * existen a propósito y no se reemplazan:
 *
 * - [LocationTracker] pide `PRIORITY_HIGH_ACCURACY` cada 2 s y tiene un
 *   consumidor legítimo —el mapa en vivo de `SaleLocationMap`, donde el punto
 *   azul tiene que seguir al teléfono mientras se mira la pantalla—.
 * - Esta clase la usa la pantalla principal, que sólo necesita saber una vez
 *   dónde está parado el cobrador para ordenar las puertas de la ruta. Un fix de
 *   alta precisión cada dos segundos, todo el día en la calle, para reordenar
 *   diez renglones que ya están en pantalla, es batería quemada sin nada a
 *   cambio: por eso acá va `PRIORITY_BALANCED_POWER_ACCURACY` y una sola
 *   petición.
 *
 * **Contesta `null`, no lanza.** Los tres caminos por los que no hay coordenada
 * —permiso negado, proveedor sin fix (GPS apagado, bajo techo) y error de Play
 * Services— devuelven `null`, que es lo que la pantalla sabe manejar: la lista
 * de cercanos simplemente no se pinta. La comprobación de permiso es explícita y
 * previa, así que la `SecurityException` ni siquiera es el camino normal.
 *
 * La cancelación **sí** se propaga: un `CancellationException` se relanza tal
 * cual, o la corrutina de la pantalla creería que terminó bien al salir de ella.
 */
class CurrentLocationReader(private val context: Context) {

    /** La posición actual, o `null` si no se puede saber. Ver el KDoc de la clase. */
    suspend fun current(): Coord? {
        if (!hasLocationPermission()) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        return try {
            client
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .await()
                ?.let { Coord(lat = it.latitude, lng = it.longitude) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Play Services sin actualizar, proveedor caído, permiso revocado a
            // media petición: nada de esto merece tumbar la pantalla principal.
            Log.w(TAG, "no se pudo leer la ubicación actual", error)
            null
        } finally {
            // Sin esto, una corrutina cancelada dejaría viva la petición al
            // proveedor: el `finally` corre también en la cancelación.
            cancellation.cancel()
        }
    }

    private fun hasLocationPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val TAG = "CurrentLocationReader"

        /**
         * Cualquiera de los dos basta: ordenar puertas por cercanía no necesita
         * precisión fina, y el permiso grueso es el que algunos equipos dan.
         */
        val PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
