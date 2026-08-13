package com.example.msp_app.core.common.location

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Texto que se muestra cuando la venta no tiene ubicación. Un guion largo, no
 * una frase: la tarjeta ya es densa y el hueco solo tiene que leerse como "acá
 * no hay dato", igual que en cualquier tabla. La lectura para accesibilidad
 * ("sin ubicación") la pone la UI como `contentDescription`.
 */
const val NO_DISTANCE_LABEL: String = "—"

private const val METERS_PER_KILOMETER = 1_000.0

/** A partir de 10 km el decimal es ruido: nadie recorre "12.4 km" distinto que "12 km". */
private const val KILOMETERS_DECIMAL_LIMIT = 10.0

/**
 * Único punto donde una [SaleDistance] se convierte en texto. Cualquier otra
 * forma de pintarla (interpolar el `Double`, por ejemplo) fue justamente el
 * defecto: `"$distancia m"` imprimía `9.223372036854776E18 m` y la notación
 * científica —un token indivisible de 20 caracteres— partía la tarjeta.
 *
 * Formato:
 * - menos de 1 km: metros enteros, `"850 m"` (los decimales de un GPS de
 *   celular son ruido).
 * - de 1 a 10 km: kilómetros con un decimal, sin `.0` sobrante — `"1.2 km"`,
 *   `"5 km"`.
 * - 10 km o más: kilómetros enteros, `"25 km"`.
 * - [SaleDistance.Unknown]: [NO_DISTANCE_LABEL].
 *
 * La unidad viaja DENTRO del texto y el resultado nunca pasa de 8 caracteres
 * (el techo es `"20000 km"`, ver [SaleDistance.MAX_PLAUSIBLE_METERS]), así que
 * ningún valor puede volver a desbordar la fila.
 */
fun SaleDistance.label(): String = when (this) {
    is SaleDistance.Unknown -> NO_DISTANCE_LABEL
    is SaleDistance.Known -> labelForMeters(meters)
}

private fun labelForMeters(meters: Double): String {
    val roundedMeters = meters.roundToLong()
    if (roundedMeters < METERS_PER_KILOMETER) return "$roundedMeters m"

    val kilometers = meters / METERS_PER_KILOMETER
    if (kilometers >= KILOMETERS_DECIMAL_LIMIT) return "${kilometers.roundToLong()} km"

    // Locale.US fija el punto decimal: el separador no puede depender del
    // idioma del dispositivo o el mismo número se leería distinto por equipo.
    val withDecimal = String.format(Locale.US, "%.1f", kilometers)
    return "${withDecimal.removeSuffix(".0")} km"
}
