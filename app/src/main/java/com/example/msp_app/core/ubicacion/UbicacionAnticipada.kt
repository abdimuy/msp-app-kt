package com.example.msp_app.core.ubicacion

import android.content.Context
import com.example.msp_app.core.common.location.captura.CapturasEnEspera
import com.example.msp_app.core.common.location.captura.EstadoDeUbicacion
import com.example.msp_app.core.common.location.captura.RelojMonotono
import com.example.msp_app.core.common.location.captura.TomadorDeUbicacion
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * El tomador del proceso (E-APP-142). Las pantallas lo inician con su propio alcance
 * (`UbicacionAnticipadaEfecto`) y quien guarda un abono, condonación o visita lo consulta, sin
 * suspender, por [deCaptura] (E-APP-146): la mejor ubicación ya tomada va con el guardado y, si no
 * hubo, la captura queda en espera y [UbicacionDeCaptura] le escribe el primer fix válido EN CUANTO
 * el tomador lo recibe (K5), no al cerrar una ventana.
 */
object UbicacionAnticipada {
    @Volatile
    private var tomador: TomadorDeUbicacion? = null

    @Volatile
    private var deCaptura: UbicacionDeCaptura? = null

    private val enEspera = CapturasEnEspera(config = { ConfigDeUbicacion.actual })

    /** Alcance de proceso: la toma que sigue a un guardado y la escritura de cada fix. */
    private val alcanceDeProceso = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val relojMonotono = RelojMonotono { android.os.SystemClock.elapsedRealtimeNanos() }

    @Synchronized
    fun tomador(context: Context): TomadorDeUbicacion = tomador ?: TomadorDeUbicacion(
        proveedor = ProveedorDeUbicacionFused(context.applicationContext),
        reloj = relojMonotono,
        config = { ConfigDeUbicacion.actual },
        reporte = TelemetriaDeUbicacion,
        alRecibirFix = { ahora, fixes -> deCaptura?.alRecibir(ahora, fixes) }
    ).also { tomador = it }

    /** La ubicación de abonos, condonaciones y visitas al guardar y justo después (E-APP-146). */
    @Synchronized
    fun deCaptura(context: Context): UbicacionAlGuardar = deCaptura ?: run {
        val app = context.applicationContext
        val db = AppDatabase.getInstance(app)
        UbicacionDeCaptura(
            db = db,
            ubicaciones = db.ubicacionDeCapturaDao(),
            tomador = tomador(app),
            enEspera = enEspera,
            monotono = relojMonotono,
            reloj = AppClock.System,
            alcance = alcanceDeProceso,
            aplicarEnLaCaptura = UbicacionDeCaptura.enLasCapturas(db.paymentDao(), db.visitDao())
        )
    }.also { deCaptura = it }

    /**
     * ¿La toma ya informó que no puede dar ubicación (permiso negado o GPS apagado)? Entonces el
     * worker no espera (K3): no va a llegar nada. `SinFix` no cuenta: una ventana agotada sin fix no
     * impide que la toma que sigue a un guardado encuentre uno.
     */
    fun noVaALlegar(context: Context): Boolean = when (tomador(context).estado.value) {
        EstadoDeUbicacion.SinPermiso, EstadoDeUbicacion.GpsApagado -> true
        else -> false
    }
}
