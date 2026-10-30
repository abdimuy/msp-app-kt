package com.example.msp_app.core.ubicacion

import android.content.Context
import com.example.msp_app.core.common.location.captura.RelojMonotono
import com.example.msp_app.core.common.location.captura.TomadorDeUbicacion

/**
 * El tomador del proceso (E-APP-142). Las pantallas lo inician con su propio alcance
 * (`UbicacionAnticipadaEfecto`) y quien guarda un abono, condonación o visita puede pedirle
 * `mejorUbicacionAntesDeGuardar()` SIN suspender. Hoy nadie del flujo de guardado lo llama: engancharlo
 * a lo que se guarda es el frente 5, detrás de la compuerta ANTES.
 */
object UbicacionAnticipada {
    @Volatile
    private var tomador: TomadorDeUbicacion? = null

    @Synchronized
    fun tomador(context: Context): TomadorDeUbicacion = tomador ?: TomadorDeUbicacion(
        proveedor = ProveedorDeUbicacionFused(context.applicationContext),
        reloj = RelojMonotono { android.os.SystemClock.elapsedRealtimeNanos() },
        config = { ConfigDeUbicacion.actual },
        reporte = TelemetriaDeUbicacion
    ).also { tomador = it }
}
