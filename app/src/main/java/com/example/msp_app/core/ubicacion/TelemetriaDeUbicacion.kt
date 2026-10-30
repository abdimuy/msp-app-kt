package com.example.msp_app.core.ubicacion

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.common.location.captura.MotivoSinUbicacion
import com.example.msp_app.core.common.location.captura.OrigenDeUbicacion
import com.example.msp_app.core.common.location.captura.ReporteDelTomador
import com.example.msp_app.core.logging.Logger
import com.example.msp_app.core.logging.RemoteLogger

/**
 * Telemetría de la ubicación anticipada (E-APP-143): tiempo hasta el primer fix, precisión, origen y
 * motivo de "sin ubicación". Sin datos personales: nunca coordenadas, ids ni nombres. Mismo patrón que
 * `TelemetriaDeVisitas`: eventos y claves en lista blanca cerrada, valores enteros >= 0, nunca lanza.
 */
object TelemetriaDeUbicacion : ReporteDelTomador {
    const val MODULO = "UBICACION"

    const val EVENTO_PRIMER_FIX = "ubicacion_primer_fix"
    const val EVENTO_SIN_UBICACION = "ubicacion_sin_ubicacion"
    const val EVENTO_GUARDADO = "ubicacion_guardado"

    val EVENTOS: Set<String> = setOf(EVENTO_PRIMER_FIX, EVENTO_SIN_UBICACION, EVENTO_GUARDADO)

    const val MS_HASTA_PRIMER_FIX = "ubicacion.ms_hasta_primer_fix"
    const val PRECISION_M = "ubicacion.precision_m"
    const val DE_ULTIMA_CONOCIDA = "ubicacion.de_ultima_conocida"
    const val MOTIVO = "ubicacion.motivo"
    const val ORIGEN = "ubicacion.origen"

    val CLAVES: Set<String> =
        setOf(MS_HASTA_PRIMER_FIX, PRECISION_M, DE_ULTIMA_CONOCIDA, MOTIVO, ORIGEN)

    /** Códigos enteros de [MotivoSinUbicacion] (estables: no reordenar). */
    fun codigoDeMotivo(motivo: MotivoSinUbicacion): Int = when (motivo) {
        MotivoSinUbicacion.SIN_PERMISO -> 0
        MotivoSinUbicacion.GPS_APAGADO -> 1
        MotivoSinUbicacion.SIN_FIX -> 2
        MotivoSinUbicacion.ERROR -> 3
    }

    /** Códigos de origen: 0 antes, 1 después, 2 ninguno. */
    fun codigoDeOrigen(origen: OrigenDeUbicacion?): Int = when (origen) {
        OrigenDeUbicacion.ANTES -> 0
        OrigenDeUbicacion.DESPUES -> 1
        null -> 2
    }

    override fun primerFix(msDesdeInicio: Long, precisionM: Int, deUltimaConocida: Boolean) =
        emitir(
            EVENTO_PRIMER_FIX,
            mapOf(
                MS_HASTA_PRIMER_FIX to msDesdeInicio,
                PRECISION_M to precisionM,
                DE_ULTIMA_CONOCIDA to if (deUltimaConocida) 1 else 0
            )
        )

    override fun sinUbicacion(motivo: MotivoSinUbicacion) =
        emitir(EVENTO_SIN_UBICACION, mapOf(MOTIVO to codigoDeMotivo(motivo)))

    /** Lo que se guardó con el pago/visita: origen (antes/después/ninguno) y precisión si hubo. */
    fun guardado(origen: OrigenDeUbicacion?, precisionM: Int?) = emitir(
        EVENTO_GUARDADO,
        buildMap {
            put(ORIGEN, codigoDeOrigen(origen))
            if (precisionM != null) put(PRECISION_M, precisionM)
        }
    )

    @VisibleForTesting
    @Volatile
    internal var sinkParaPrueba: ((String, Map<String, Any>) -> Unit)? = null

    @Suppress("TooGenericExceptionCaught")
    fun emitir(evento: String, datos: Map<String, Any>) {
        try {
            if (evento !in EVENTOS) return
            val limpio = datos.filter { (clave, valor) -> clave in CLAVES && valorPermitido(valor) }
            if (limpio.isEmpty()) return
            val sink = sinkParaPrueba
            if (sink != null) {
                sink(evento, limpio)
            } else {
                Logger.get().logEvent(
                    module = MODULO,
                    eventType = RemoteLogger.EventType.SYSTEM_EVENT,
                    eventName = evento,
                    data = limpio
                )
            }
        } catch (e: Throwable) {
            runCatching { Log.w(TAG, "telemetria de ubicacion no se pudo mandar", e) }
        }
    }

    private fun valorPermitido(valor: Any): Boolean = when (valor) {
        is Int -> valor >= 0
        is Long -> valor >= 0L
        else -> false
    }

    private const val TAG = "TelemetriaDeUbicacion"
}
