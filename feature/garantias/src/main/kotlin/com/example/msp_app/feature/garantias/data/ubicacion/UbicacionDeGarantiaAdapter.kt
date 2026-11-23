package com.example.msp_app.feature.garantias.data.ubicacion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.example.msp_app.feature.garantias.domain.port.UbicacionDeGarantiaPort
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * La ubicación del cobrador sobre Play Services.
 *
 * **No arranca `UpdateLocationService`**: la captura pide las coordenadas una
 * vez, en el momento de guardar, igual que la visita. Colgarse de un servicio
 * de ubicación en primer plano acoplaría la garantía a un defecto conocido del
 * camino viejo.
 *
 * **Contesta `null`, no lanza**, cuando falta el permiso o el proveedor falla.
 * El puerto define la ausencia como un estado legítimo de la recolección (D5):
 * la pantalla dice "Sin ubicación" y el guardado sigue. En la entrega es
 * distinto, y lo decide el dominio: sin coordenadas no se encola.
 */
class UbicacionDeGarantiaAdapter @Inject constructor(
    @ApplicationContext private val context: Context
) : UbicacionDeGarantiaPort {

    @Suppress("TooGenericExceptionCaught") // Play Services falla con tipos dispares
    override suspend fun ubicacionActual(): Ubicacion? {
        if (!tienePermiso()) return null
        val cliente = LocationServices.getFusedLocationProviderClient(context)
        val cancelacion = CancellationTokenSource()
        return try {
            cliente
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancelacion.token)
                .await()
                ?.let { Ubicacion.de(it.latitude, it.longitude) }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (_: Exception) {
            null
        } finally {
            // Sin esto, una corrutina cancelada dejaría viva la petición al
            // proveedor: el `finally` corre también en la cancelación.
            cancelacion.cancel()
        }
    }

    private fun tienePermiso(): Boolean = PERMISOS.any {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        /** Cualquiera de los dos basta: la recolección no necesita precisión fina. */
        val PERMISOS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
