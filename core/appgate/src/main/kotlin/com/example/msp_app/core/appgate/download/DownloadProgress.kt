package com.example.msp_app.core.appgate.download

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BYTES_PER_MEGABYTE = 1_000_000.0

/** Tolerancia para decidir si un valor en megas se muestra sin decimal. */
private const val WHOLE_MEGABYTE_EPSILON = 0.05

/**
 * Cuánto va de la descarga. Se mide en bytes y se **muestra en megas**: una
 * rueda indeterminada es lo que termina en una llamada por teléfono.
 *
 * [totalBytes] puede llegar en `0` cuando el servidor no anuncia tamaño; en
 * ese caso [fraction] vale `0` y la UI cae al modo "sin barra".
 */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long
) {
    /** `0f..1f`. Vale `0f` si no se conoce el total. */
    val fraction: Float
        get() = if (totalBytes <= 0L) {
            0f
        } else {
            (downloadedBytes.toFloat() / totalBytes).coerceIn(
                0f,
                1f
            )
        }

    val percent: Int
        get() = (fraction * PERCENT_SCALE).roundToInt()

    /** `true` cuando ya no falta nada por bajar. */
    val complete: Boolean
        get() = totalBytes > 0L && downloadedBytes >= totalBytes

    private companion object {
        const val PERCENT_SCALE = 100
    }
}

/**
 * Bytes → megas legibles: `"4.2"`, `"11"`.
 *
 * Un decimal, y sin el `.0` cuando es redondo — que es como está escrito el
 * mockup aprobado ("4.2 de 11 MB"). [Locale.US] a propósito: fija el punto
 * decimal para que el texto no cambie de forma según el idioma del teléfono.
 */
fun formatMegabytes(bytes: Long): String {
    val megabytes = bytes / BYTES_PER_MEGABYTE
    val rounded = megabytes.roundToInt()
    return if (abs(megabytes - rounded) < WHOLE_MEGABYTE_EPSILON) {
        rounded.toString()
    } else {
        String.format(Locale.US, "%.1f", megabytes)
    }
}

/** `"4.2 de 11 MB"` — el texto exacto bajo la barra de progreso. */
fun DownloadProgress.megabytesLabel(): String =
    "${formatMegabytes(downloadedBytes)} de ${formatMegabytes(totalBytes)} MB"

/** `"11 MB"` — el peso que el botón de descarga con datos móviles anuncia. */
fun megabytesLabel(bytes: Long): String = "${formatMegabytes(bytes)} MB"
