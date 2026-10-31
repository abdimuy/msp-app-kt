package com.example.msp_app.core.common.location.captura

import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion.Rechazar
import java.time.Instant

/**
 * Ayudas compartidas de las pruebas de [AceptacionDeUbicacion] (E-APP-140): fabricas de fixes con
 * el reloj monotono fijo en [GUARDADO_NANOS]. Sin pruebas propias.
 */
abstract class AceptacionDeUbicacionBase {

    protected val guardado: Instant = Instant.parse("2026-10-30T18:00:00Z")
    protected val cfg = ConfigDeAceptacion.POR_DEFECTO

    protected fun fix(
        segundos: Double,
        precision: Double = 10.0,
        lat: Double? = null,
        lon: Double = BASE_LON,
        velocidad: Double? = null,
        mock: Boolean = false
    ): FixDeUbicacion = fx(
        lat ?: BASE_LAT,
        lon,
        precision,
        Math.round(segundos * MS),
        velocidad,
        mock
    )

    /** Fix a [ms] milisegundos del guardado en el reloj monótono; la hora de pared acompaña igual. */
    protected fun fx(
        lat: Double,
        lon: Double,
        precision: Double,
        ms: Long,
        velocidad: Double? = null,
        mock: Boolean = false
    ) = FixDeUbicacion(
        lat = lat,
        lon = lon,
        precisionM = precision,
        hora = guardado.plusMillis(ms),
        elapsedRealtimeNanos = GUARDADO_NANOS + ms * 1_000_000L,
        velocidadMs = velocidad,
        esMock = mock
    )

    /** Metros al norte de la base, en grados de latitud. */
    protected fun norte(metros: Double): Double = BASE_LAT + metros / METROS_POR_GRADO

    protected fun decidir(vararg fixes: FixDeUbicacion, config: ConfigDeAceptacion = cfg) =
        AceptacionDeUbicacion.decidir(GUARDADO_NANOS, fixes.toList(), config)

    protected fun motivo(d: DecisionDeUbicacion): MotivoDeRechazo? = (d as? Rechazar)?.motivo
}

internal const val BASE_LAT = 20.5888
internal const val BASE_LON = -100.3899
internal const val METROS_POR_GRADO = 111_194.9
internal const val MS = 1000.0
internal const val SEMILLA = 140L
internal const val GUARDADO_NANOS = 5_000_000_000_000L
internal const val ITERACIONES = 2000
