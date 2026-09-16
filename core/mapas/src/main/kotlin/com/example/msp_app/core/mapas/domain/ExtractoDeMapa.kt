package com.example.msp_app.core.mapas.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **El paquete del extracto de mapa**: de dónde se baja y cuánto pesa.
 *
 * ## Por qué el `.pmtiles` NO está en el repo
 *
 * `CLAUDE.md` §5 dice que este repositorio es **público**. El extracto pesa
 * ~25 MB y git no olvida; además ataría la versión del mapa a la versión de la
 * app, cuando lo que se quiere es poder regenerarlo del build diario de
 * Protomaps sin tocar el código.
 *
 * ## Por qué NO lleva `sha256`
 *
 * Ver el KDoc de [CabeceraDePmtiles]. El extracto se regenera; un checksum
 * escrito en el código quedaría mal en el primer `pmtiles extract` nuevo. La
 * completitud sale de la propia cabecera del archivo.
 *
 * [tamanoBytes] es **el número que la pantalla anuncia**, y nada más: no se usa
 * para aceptar ni rechazar el archivo. Si estuviera desactualizado, la barra de
 * avance se vería rara y la verificación seguiría siendo correcta.
 */
data class ExtractoDeMapa(
    val url: String,
    val tamanoBytes: Long
) {
    init {
        require(url.isNotBlank()) { "un extracto sin url no se puede bajar" }
        require(tamanoBytes > 0L) { "un extracto sin tamano no se puede anunciar" }
    }
}

/**
 * En qué punto está el extracto. Una sola fuente: el suelo del mapa no infiere
 * nada de la existencia del archivo, lo pregunta acá.
 */
sealed interface EstadoDelExtracto {

    /**
     * **No hay de dónde bajarlo.** El extracto se genera a mano
     * (`go-pmtiles extract`) y hoy no está publicado en ningún servidor, así
     * que `EXTRACTO_DE_MAPA_URL` viene vacío.
     *
     * Es un estado propio y no `Ausente` a propósito: "Descargar" sobre un
     * origen que no existe es un botón que no puede funcionar, y ofrecerlo
     * sería mentir con un afordante. Con `SinOrigen` la pantalla dice lo que
     * pasa y no ofrece nada.
     */
    data object SinOrigen : EstadoDelExtracto

    /** Hay de dónde bajarlo y no está. La pantalla ofrece bajarlo. */
    data object Ausente : EstadoDelExtracto

    /**
     * Encolado, esperando wifi. **Ésta es la diferencia entre decirlo y
     * cumplirlo:** el trabajo existe y no corre hasta que la red no sea medida.
     */
    data object EsperandoWifi : EstadoDelExtracto

    /** Bajando. [avance] es lo que pinta la barra y el "12 de 25.5 MB". */
    data class Descargando(val avance: AvanceDeLaDescarga) : EstadoDelExtracto

    /** Está completo y verificado. A partir de acá el suelo dibuja calles. */
    data class Listo(val mapa: MapaDeLaRuta) : EstadoDelExtracto

    /**
     * Se cortó. Lo bajado **se conserva** y se reanuda con `Range`; decirle al
     * cobrador que no perdió los 12 MB es lo que hace que vuelva a intentar.
     */
    data class Interrumpido(val avance: AvanceDeLaDescarga) : EstadoDelExtracto
}

/**
 * Cuánto va. En bytes por dentro y **en megas por fuera**.
 *
 * Réplica deliberada del tipo homónimo de `:core:speech`, que a su vez replica
 * el `DownloadProgress` de `:core:appgate`. Son cincuenta líneas; hacer que
 * `:core:mapas` dependa de `:core:speech` para reusar dos `Long` ataría el mapa
 * al dictado, que es peor. La misma decisión, escrita por tercera vez a
 * propósito.
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

    /** `"12 de 25.5 MB"` — al lado de "Descargando…". */
    val enMegas: String get() = "${megas(bytesBajados)} de ${megas(bytesTotales)} MB"
}

private const val BYTES_POR_MEGA = 1_000_000.0

/** Tolerancia para mostrar una mega redonda sin el `.0`. */
private const val EPSILON_DE_MEGA = 0.05

/**
 * Bytes → megas legibles: `"25.5"`, `"12"`.
 *
 * **Megas decimales (10⁶), igual que `:core:speech` y `:core:appgate`.** Por eso
 * el extracto se anuncia como **25.5 MB** y no como los "24 MB" de
 * `huella-geografica-medida.md`: aquel número son mebibytes (2²⁰). Los dos son
 * el mismo archivo de 25 507 515 bytes; la app dice una sola de las dos cosas y
 * dice la que ya venía diciendo para el modelo de voz.
 *
 * [Locale.US] a propósito: fija el punto decimal para que el texto no cambie de
 * forma según el idioma del teléfono.
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
