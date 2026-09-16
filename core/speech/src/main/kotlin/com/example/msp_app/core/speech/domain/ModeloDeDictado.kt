package com.example.msp_app.core.speech.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **El paquete del modelo de alta fidelidad**: qué se baja, cuánto pesa y con
 * qué se comprueba que llegó entero.
 *
 * ## Por qué el `.bin` NO está en el repo
 *
 * `CLAUDE.md` §5 dice que este repositorio es **público**. Un binario de ~75 MB
 * committeado lo engorda para siempre —git no olvida— y además ataría la
 * versión del modelo a la versión de la app: cambiar de `tiny` a `base` sería
 * un release. Se descarga, y el `.gitignore` del módulo no tiene que decir nada
 * porque el archivo nunca vive dentro del árbol de fuentes.
 *
 * ## Por qué lleva `sha256`
 *
 * Mismo motivo que el APK de `:core:appgate`: una descarga cortada a mitad de
 * un byte produce un `.bin` que whisper.cpp abre y usa para producir **basura
 * plausible**. Una transcripción basura es peor que ninguna, porque nadie la
 * detecta leyéndola. El checksum convierte eso en "vuelve a intentar".
 */
data class ModeloDeDictado(
    val url: String,
    val tamanoBytes: Long,
    val sha256: String
) {
    init {
        require(url.isNotBlank()) { "un modelo sin url no se puede bajar" }
        require(tamanoBytes > 0L) { "un modelo sin tamano no se puede anunciar" }
        require(sha256.isNotBlank()) { "un modelo sin checksum no se puede verificar" }
    }
}

/**
 * En qué punto está el modelo. Es lo que pinta la pantalla de descarga, y es
 * **una sola fuente**: el campo de dictado no lo lee (no le importa) y la
 * pantalla no infiere nada de la existencia del archivo.
 */
sealed interface EstadoDelModelo {

    /** No está y no se está bajando. La pantalla ofrece bajarlo. */
    data object Ausente : EstadoDelModelo

    /**
     * Encolado, esperando wifi. **Ésta es la diferencia entre decirlo y
     * cumplirlo:** el trabajo existe y no corre hasta que la red no sea medida.
     */
    data object EsperandoWifi : EstadoDelModelo

    /** Bajando. [avance] es lo que pinta la barra y el "48 de 75 MB". */
    data class Descargando(val avance: AvanceDeLaDescarga) : EstadoDelModelo

    /** Está completo y verificado. A partir de acá el dictado usa whisper. */
    data object Listo : EstadoDelModelo

    /**
     * Se cortó. Lo bajado **se conserva** y se reanuda con `Range`; decirle al
     * cobrador que no perdió los 48 MB es lo que hace que vuelva a intentar.
     */
    data class Interrumpido(val avance: AvanceDeLaDescarga) : EstadoDelModelo
}

/**
 * Cuánto va. En bytes por dentro y **en megas por fuera**: una rueda
 * indeterminada sobre 75 MB con señal de barrio es lo que termina en una
 * llamada por teléfono.
 *
 * Réplica deliberada de `:core:appgate`'s `DownloadProgress` y no un `import`
 * de aquél: ese tipo es del paquete de actualización de la app, y hacer que
 * `:core:speech` dependa de `:core:appgate` para reusar un par de `Long`
 * ataría la descarga del modelo a la compuerta de versión. Son cincuenta
 * líneas; acoplar dos módulos cuesta más.
 */
data class AvanceDeLaDescarga(
    val bytesBajados: Long,
    val bytesTotales: Long
) {
    /** `0f..1f`. Vale `0f` si no se conoce el total. */
    val fraccion: Float
        get() = if (bytesTotales <= 0L) {
            0f
        } else {
            (bytesBajados.toFloat() / bytesTotales).coerceIn(0f, 1f)
        }

    /** `"48 de 75 MB"` — el texto exacto del mock, al lado de "Descargando…". */
    val enMegas: String get() = "${megas(bytesBajados)} de ${megas(bytesTotales)} MB"
}

private const val BYTES_POR_MEGA = 1_000_000.0

/** Tolerancia para mostrar una mega redonda sin el `.0`. */
private const val EPSILON_DE_MEGA = 0.05

/**
 * Bytes → megas legibles: `"75"`, `"4.2"`.
 *
 * [Locale.US] a propósito, igual que `formatMegabytes` de `:core:appgate`: fija
 * el punto decimal para que el texto no cambie de forma según el idioma del
 * teléfono.
 */
fun megas(bytes: Long): String {
    val enMegas = bytes / BYTES_POR_MEGA
    val redondo = enMegas.roundToInt()
    return if (abs(enMegas - redondo) < EPSILON_DE_MEGA) {
        redondo.toString()
    } else {
        String.format(Locale.US, "%.1f", enMegas)
    }
}
