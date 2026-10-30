package com.example.msp_app.core.common.location.captura

import java.time.Instant
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Un fix de ubicación tal como lo entrega el proveedor, sin tipos de Android.
 *
 * [elapsedRealtimeNanos] es el reloj MONÓTONO del fix (`Location.elapsedRealtimeNanos`): con él
 * se mide la antigüedad, porque la hora de pared puede saltar (cambio de hora, red, usuario).
 * [hora] es la hora de pared del fix y solo se conserva para guardarla y reportarla.
 * [precisionM] es el radio de error en metros. [velocidadMs] es opcional.
 * [esMock] es `Location.isMock`: una ubicación simulada nunca se acepta.
 */
data class FixDeUbicacion(
    val lat: Double,
    val lon: Double,
    val precisionM: Double,
    val hora: Instant,
    val elapsedRealtimeNanos: Long,
    val velocidadMs: Double? = null,
    val esMock: Boolean = false
)

/** De qué lado del guardado salió la ubicación aceptada. */
enum class OrigenDeUbicacion { ANTES, DESPUES }

/** Por qué no se aceptó ningún fix. Los nombres son estables: viajan a telemetría. */
enum class MotivoDeRechazo {
    /** No había ningún fix. */
    SIN_FIX,

    /** NaN, infinito, fuera de rango o exactamente 0,0 (centinela "sin fix"). */
    COORDENADAS_INVALIDAS,

    /** El proveedor marcó el fix como simulado (`isMock`). */
    UBICACION_SIMULADA,

    /** Precisión NaN, infinita, cero o negativa: un fix que no dice cuánto se equivoca no sirve. */
    PRECISION_INVALIDA,

    /** Precisión peor o igual al máximo permitido. */
    PRECISION_INSUFICIENTE,

    /** Más lejos del instante de guardado que la edad máxima. */
    EDAD_EXCEDIDA,

    /** Entre el guardado y el fix el teléfono se movió (se derivó de los propios fixes). */
    CON_MOVIMIENTO
}

/** Resultado de [AceptacionDeUbicacion.decidir]. */
sealed interface DecisionDeUbicacion {
    /** Hora del fix aceptado, o `null` si se rechazó. */
    val horaDelFix: Instant?
        get() = (this as? Aceptada)?.fix?.hora

    /** Precisión del fix aceptado, o `null` si se rechazó. */
    val precisionM: Double?
        get() = (this as? Aceptada)?.fix?.precisionM

    /** Origen del fix aceptado, o `null` si se rechazó. */
    val origen: OrigenDeUbicacion?
        get() = when (this) {
            is AceptarAntes -> OrigenDeUbicacion.ANTES
            is AceptarDespues -> OrigenDeUbicacion.DESPUES
            is Rechazar -> null
        }

    sealed interface Aceptada : DecisionDeUbicacion {
        val fix: FixDeUbicacion
    }

    data class AceptarAntes(override val fix: FixDeUbicacion) : Aceptada

    data class AceptarDespues(override val fix: FixDeUbicacion) : Aceptada

    data class Rechazar(val motivo: MotivoDeRechazo) : DecisionDeUbicacion
}

/**
 * Umbrales de la regla. Los valores por defecto son los de la decisión del dueño del
 * 2026-10-30 (30 s, 30 m, cobrador quieto, sin mock; el dueño los fijó el 2026-10-30 tras la compuerta ANTES), editables sin publicar app vía `config/ubicacion` (E-APP-141) y ajustables midiendo (E-APP-140).
 *
 * Construcción directa valida y lanza [IllegalArgumentException]. [desdeMapa] es la
 * entrada para un mecanismo remoto: nunca lanza; una clave ausente, de otro tipo o fuera
 * de rango cae al valor por defecto (conservador), clave por clave.
 */
data class ConfigDeAceptacion(
    /** Edad máxima, INCLUSIVA: un fix a exactamente este número de segundos del guardado sirve. */
    val maxEdadSeg: Long = DEF_MAX_EDAD_SEG,
    /** Precisión máxima, EXCLUSIVA: un fix de exactamente este radio NO sirve ("menos de 50 m"). */
    val maxPrecisionM: Double = DEF_MAX_PRECISION_M,
    /** Piso del umbral de movimiento: por debajo de esto el desplazamiento es ruido del GPS. */
    val minMovimientoM: Double = DEF_MIN_MOVIMIENTO_M,
    /** Velocidad por encima de la cual (estricto) el fix declara que el teléfono se mueve. */
    val maxVelocidadMs: Double = DEF_MAX_VELOCIDAD_MS
) {
    init {
        require(maxEdadSeg in RANGO_EDAD) { "maxEdadSeg fuera de rango: $maxEdadSeg" }
        require(maxPrecisionM.isFinite() && maxPrecisionM in RANGO_PRECISION) {
            "maxPrecisionM fuera de rango: $maxPrecisionM"
        }
        require(minMovimientoM.isFinite() && minMovimientoM in RANGO_MOVIMIENTO) {
            "minMovimientoM fuera de rango: $minMovimientoM"
        }
        require(maxVelocidadMs.isFinite() && maxVelocidadMs in RANGO_VELOCIDAD) {
            "maxVelocidadMs fuera de rango: $maxVelocidadMs"
        }
    }

    companion object {
        const val CLAVE_MAX_EDAD_SEG = "max_edad_seg"
        const val CLAVE_MAX_PRECISION_M = "max_precision_m"
        const val CLAVE_MIN_MOVIMIENTO_M = "min_movimiento_m"
        const val CLAVE_MAX_VELOCIDAD_MS = "max_velocidad_ms"

        const val DEF_MAX_EDAD_SEG = 30L
        const val DEF_MAX_PRECISION_M = 30.0
        const val DEF_MIN_MOVIMIENTO_M = 15.0
        const val DEF_MAX_VELOCIDAD_MS = 1.0

        val RANGO_EDAD: LongRange = 1L..600L
        val RANGO_PRECISION: ClosedFloatingPointRange<Double> = 1.0..500.0
        val RANGO_MOVIMIENTO: ClosedFloatingPointRange<Double> = 1.0..200.0
        val RANGO_VELOCIDAD: ClosedFloatingPointRange<Double> = 0.1..10.0

        /** Config por defecto. */
        val POR_DEFECTO = ConfigDeAceptacion()

        /** Construye desde un mapa de claves (p. ej. un documento remoto). No lanza. */
        fun desdeMapa(mapa: Map<String, Any?>): ConfigDeAceptacion {
            val edad = (mapa[CLAVE_MAX_EDAD_SEG] as? Number)?.let(::enteroExacto)
                ?.takeIf { it in RANGO_EDAD } ?: DEF_MAX_EDAD_SEG
            return ConfigDeAceptacion(
                maxEdadSeg = edad,
                maxPrecisionM = decimalEnRango(
                    mapa[CLAVE_MAX_PRECISION_M],
                    RANGO_PRECISION,
                    DEF_MAX_PRECISION_M
                ),
                minMovimientoM = decimalEnRango(
                    mapa[CLAVE_MIN_MOVIMIENTO_M],
                    RANGO_MOVIMIENTO,
                    DEF_MIN_MOVIMIENTO_M
                ),
                maxVelocidadMs = decimalEnRango(
                    mapa[CLAVE_MAX_VELOCIDAD_MS],
                    RANGO_VELOCIDAD,
                    DEF_MAX_VELOCIDAD_MS
                )
            )
        }

        private fun enteroExacto(n: Number): Long? {
            val d = n.toDouble()
            return if (d.isFinite() && d == Math.floor(d)) n.toLong() else null
        }

        private fun decimalEnRango(
            valor: Any?,
            rango: ClosedFloatingPointRange<Double>,
            defecto: Double
        ): Double {
            val d = (valor as? Number)?.toDouble()
            return if (d != null && d.isFinite() && d in rango) d else defecto
        }
    }
}

/**
 * Regla pura de aceptación de la ubicación de un abono, condonación o visita
 * (E-APP-140; decisión del dueño 2026-10-30, puntos 2 a 4).
 *
 * Sin Android y sin reloj global: todo entra por parámetro, así que es determinista.
 *
 * - La antigüedad se mide con el reloj monótono (`elapsedRealtimeNanos`), nunca con la hora de pared.
 *   Un fix simulado (`esMock`) se rechaza siempre y no cuenta para el movimiento.
 * - ANTES: gana el fix más cercano al instante de guardado, anterior o igual a él, con edad
 *   <= `maxEdadSeg` y precisión < `maxPrecisionM`. No mira movimiento.
 * - DESPUES (solo si no hubo ANTES): el primer fix posterior al guardado con edad <=
 *   `maxEdadSeg` y precisión < `maxPrecisionM`, si no hay movimiento. El movimiento se
 *   deriva de los propios fixes: se arma una cadena con el último fix previo al guardado
 *   (de cualquier edad y precisión válida) y todos los fixes utilizables posteriores dentro
 *   de la ventana; hay movimiento si dos cualesquiera se separan más que
 *   `max(raíz(pA² + pB²), minMovimientoM)` o si algún fix de la cadena declara velocidad >
 *   `maxVelocidadMs`. Más fixes observados solo pueden convertir una aceptación en rechazo.
 *   No usa ACTIVITY_RECOGNITION (decisión del dueño).
 * - Coordenadas inválidas (NaN, infinito, fuera de rango, 0,0) siempre se ignoran.
 * - El orden de la lista de entrada no importa.
 */
object AceptacionDeUbicacion {

    private const val RADIO_TIERRA_M = 6_371_000.0
    private const val GRADOS_A_RADIANES = Math.PI / 180.0
    private const val LAT_MAX = 90.0
    private const val LON_MAX = 180.0

    private const val NANOS_POR_SEG = 1_000_000_000L

    /**
     * [guardadoElapsedNanos] es el instante de guardado en el MISMO reloj monótono de los fixes
     * (`SystemClock.elapsedRealtimeNanos()` al guardar).
     */
    fun decidir(
        guardadoElapsedNanos: Long,
        fixes: List<FixDeUbicacion>,
        config: ConfigDeAceptacion = ConfigDeAceptacion.POR_DEFECTO
    ): DecisionDeUbicacion {
        if (fixes.isEmpty()) return DecisionDeUbicacion.Rechazar(MotivoDeRechazo.SIN_FIX)
        val ordenados = fixes.sortedWith(ORDEN_TOTAL)

        val mejorAntes = ordenados
            .filter {
                edad(it, guardadoElapsedNanos) <= 0 && motivoIndividual(it, guardadoElapsedNanos, config) == null
            }
            .maxWithOrNull(
                compareBy<FixDeUbicacion> {
                    it.elapsedRealtimeNanos
                }.thenByDescending { it.precisionM }
            )
        if (mejorAntes != null) return DecisionDeUbicacion.AceptarAntes(mejorAntes)

        val posteriores = ordenados.filter {
            edad(it, guardadoElapsedNanos) > 0 && motivoIndividual(it, guardadoElapsedNanos, config) == null
        }
        val candidato = posteriores.firstOrNull()
            ?: return DecisionDeUbicacion.Rechazar(motivoDelMasCercano(ordenados, guardadoElapsedNanos, config))

        val referencia = ordenados.lastOrNull {
            edad(it, guardadoElapsedNanos) <= 0 && coordenadasValidas(it) && precisionValida(it) && !it.esMock
        }
        val cadena = listOfNotNull(referencia) + posteriores
        return if (hayMovimiento(cadena, config)) {
            DecisionDeUbicacion.Rechazar(MotivoDeRechazo.CON_MOVIMIENTO)
        } else {
            DecisionDeUbicacion.AceptarDespues(candidato)
        }
    }

    /** Distancia haversine en metros. Pública para que las pruebas y la telemetría no la dupliquen. */
    fun distanciaM(a: FixDeUbicacion, b: FixDeUbicacion): Double {
        val dLat = (b.lat - a.lat) * GRADOS_A_RADIANES
        val dLon = (b.lon - a.lon) * GRADOS_A_RADIANES
        val h = sin(dLat / 2).let { it * it } +
            cos(a.lat * GRADOS_A_RADIANES) * cos(b.lat * GRADOS_A_RADIANES) * sin(dLon / 2).let { it * it }
        return 2 * RADIO_TIERRA_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun hayMovimiento(cadena: List<FixDeUbicacion>, config: ConfigDeAceptacion): Boolean {
        if (cadena.any { (it.velocidadMs ?: 0.0) > config.maxVelocidadMs }) return true
        // Todos los pares, no solo los consecutivos: una deriva lenta (cada paso bajo el umbral)
        // también cuenta, y agregar fixes solo puede agregar pares, nunca quitar movimiento.
        return cadena.indices.any { i ->
            (i + 1 until cadena.size).any { j -> separados(cadena[i], cadena[j], config) }
        }
    }

    private fun separados(
        a: FixDeUbicacion,
        b: FixDeUbicacion,
        config: ConfigDeAceptacion
    ): Boolean {
        val combinada = sqrt(a.precisionM * a.precisionM + b.precisionM * b.precisionM)
        return distanciaM(a, b) > maxOf(combinada, config.minMovimientoM)
    }

    private fun motivoDelMasCercano(
        ordenados: List<FixDeUbicacion>,
        guardadoElapsedNanos: Long,
        config: ConfigDeAceptacion
    ): MotivoDeRechazo {
        val masCercano = ordenados.minBy { abs(edad(it, guardadoElapsedNanos)) }
        // Un fix posterior fuera de ventana es edad excedida aunque su precisión también falle.
        return motivoIndividual(masCercano, guardadoElapsedNanos, config) ?: MotivoDeRechazo.EDAD_EXCEDIDA
    }

    /** `null` si el fix, por sí solo, sirve; si no, la primera razón por la que no. */
    private fun motivoIndividual(
        fix: FixDeUbicacion,
        guardadoElapsedNanos: Long,
        config: ConfigDeAceptacion
    ): MotivoDeRechazo? = when {
        !coordenadasValidas(fix) -> MotivoDeRechazo.COORDENADAS_INVALIDAS
        !precisionValida(fix) -> MotivoDeRechazo.PRECISION_INVALIDA
        fix.esMock -> MotivoDeRechazo.UBICACION_SIMULADA
        abs(edad(fix, guardadoElapsedNanos)) > config.maxEdadSeg * NANOS_POR_SEG -> MotivoDeRechazo.EDAD_EXCEDIDA
        fix.precisionM >= config.maxPrecisionM -> MotivoDeRechazo.PRECISION_INSUFICIENTE
        else -> null
    }

    /** Diferencia monótona fix menos guardado, saturada para no desbordar con relojes absurdos. */
    private fun edad(fix: FixDeUbicacion, guardadoElapsedNanos: Long): Long = try {
        Math.subtractExact(fix.elapsedRealtimeNanos, guardadoElapsedNanos)
    } catch (_: ArithmeticException) {
        if (fix.elapsedRealtimeNanos > guardadoElapsedNanos) Long.MAX_VALUE else Long.MIN_VALUE + 1
    }

    private fun coordenadasValidas(fix: FixDeUbicacion): Boolean =
        fix.lat.isFinite() && fix.lon.isFinite() &&
            abs(fix.lat) <= LAT_MAX && abs(fix.lon) <= LON_MAX &&
            !(fix.lat == 0.0 && fix.lon == 0.0)

    private fun precisionValida(fix: FixDeUbicacion): Boolean =
        fix.precisionM.isFinite() && fix.precisionM > 0.0

    private val ORDEN_TOTAL: Comparator<FixDeUbicacion> = compareBy<FixDeUbicacion> {
        it.elapsedRealtimeNanos
    }
        .thenBy { it.hora }
        .thenBy { it.lat }
        .thenBy { it.lon }
        .thenBy { it.precisionM }
        .thenBy { it.velocidadMs ?: Double.NEGATIVE_INFINITY }
        .thenBy { it.esMock }
}
