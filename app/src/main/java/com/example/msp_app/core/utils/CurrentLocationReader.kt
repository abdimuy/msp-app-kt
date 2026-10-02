package com.example.msp_app.core.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * De dónde saca la pantalla principal la posición del cobrador, **como flujo**.
 *
 * Existe para que la pantalla no construya a Play Services por dentro: en
 * producción es [CurrentLocationReader]; en las pruebas, un
 * `MutableSharedFlow` que emite las posiciones que la prueba decide.
 */
fun interface FuenteDePosicion {
    fun updates(): Flow<Coord>
}

/**
 * **Dónde está el cobrador, para ordenar la lista de clientes cercanos.**
 *
 * Es el hermano barato de [LocationTracker]. Los dos existen a propósito y no se
 * reemplazan:
 *
 * - [LocationTracker] pide `PRIORITY_HIGH_ACCURACY` cada 2 s y tiene un
 *   consumidor legítimo —el mapa en vivo de `SaleLocationMap`, donde el punto
 *   azul tiene que seguir al teléfono mientras se mira la pantalla—.
 * - Esta clase la usa la pantalla principal para ordenar las puertas de la ruta.
 *   Del 2026-09-22 (`0471941f`) al 2026-09-26 leía **una sola vez**, y la lista
 *   se quedaba con la primera lectura mientras el cobrador avanzaba: lo reportó
 *   el dueño y **el 2026-09-26 decidió que la lista siga al cobrador**. Por eso
 *   [updates] es un flujo, cada [INTERVALO_MS] como máximo y sólo tras moverse
 *   [DISTANCIA_MINIMA_M] — no cada dos segundos como el mapa.
 * - **Alta precisión siempre** (`PRIORITY_HIGH_ACCURACY`, decisión del dueño del
 *   2026-10-02): con `BALANCED_POWER_ACCURACY` el proveedor resolvía por wifi y
 *   antenas, con errores de decenas a cientos de metros, y los cobradores
 *   reportaban distancias que "no marcan bien". No se le pregunta nada al
 *   cobrador: la app ya pide `ACCESS_FINE_LOCATION` al arrancar.
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
class CurrentLocationReader(private val context: Context) : FuenteDePosicion {

    /**
     * La posición del cobrador mientras alguien escuche. Emite primero
     * [current] —para no esperar el primer intervalo con la lista vacía— y luego
     * cada actualización del proveedor. Sin permiso termina sin emitir; al
     * cancelarse quita la petición al proveedor.
     */
    @SuppressLint("MissingPermission")
    override fun updates(): Flow<Coord> = callbackFlow {
        current()?.let { send(it) }
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            INTERVALO_MS
        )
            .setMinUpdateIntervalMillis(INTERVALO_MINIMO_MS)
            .setMinUpdateDistanceMeters(DISTANCIA_MINIMA_M)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(Coord(lat = it.latitude, lng = it.longitude)) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Mismo criterio que [current]: sin actualizaciones la lista se queda
            // con la última posición conocida, no se tumba la pantalla.
            Log.w(TAG, "no se pudieron pedir actualizaciones de ubicación", error)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    /** La posición actual, o `null` si no se puede saber. Ver el KDoc de la clase. */
    suspend fun current(): Coord? {
        if (!hasLocationPermission()) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        return try {
            client
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
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

        /** Cada cuánto se desea una posición nueva: un cobrador a pie o en moto. */
        const val INTERVALO_MS = 20_000L

        /** Tope de frecuencia aunque otra app pida ubicación más seguido. */
        const val INTERVALO_MINIMO_MS = 15_000L

        /**
         * Parado frente a una puerta no se reordena nada. 10 m y no 30: con alta
         * precisión el ruido del GPS ya cabe en ese margen, y en una calle de
         * casas juntas 30 m eran dos o tres puertas sin reordenar.
         */
        const val DISTANCIA_MINIMA_M = 10f

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
