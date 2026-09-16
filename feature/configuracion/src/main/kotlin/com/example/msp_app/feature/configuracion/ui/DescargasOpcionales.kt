package com.example.msp_app.feature.configuracion.ui

import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.mapas.domain.megas as megasDelMapa
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.speech.domain.megas as megasDelDictado

/**
 * **Las dos descargas opcionales, en el vocabulario de una sola sección.**
 *
 * `:core:speech` y `:core:mapas` tienen cada uno su `EstadoDel…`, y son
 * distintos a propósito: el mapa tiene un estado que el dictado no puede tener
 * (`SinOrigen`) y el suyo de "listo" carga el mapa ya abierto. Pero la sección
 * de Configuración pinta **dos renglones iguales**, y dos renglones iguales
 * necesitan un vocabulario común. Éste es ese vocabulario, y vive acá —del lado
 * del que pinta— y no en ninguno de los dos módulos: ninguno de los dos tiene
 * por qué saber que el otro existe.
 */
enum class DescargaOpcional {

    /** El modelo de alta fidelidad de `:core:speech`. */
    DICTADO,

    /** El extracto de calles de `:core:mapas`. */
    MAPA
}

/**
 * En qué punto está una descarga opcional.
 *
 * [SIN_ORIGEN] es el estado que hoy tiene el mapa de verdad: el `.pmtiles` se
 * genera a mano y **no está publicado en ningún servidor**, así que no hay de
 * dónde bajarlo. Es un estado propio y no [AUSENTE] por la misma razón que en
 * `:core:mapas`: ofrecer una descarga que no puede ocurrir es mentir con un
 * afordante.
 */
enum class EstadoDeLaDescarga {

    /** No hay de dónde bajarlo. Nada que ofrecer, y el renglón lo dice. */
    SIN_ORIGEN,

    /** Hay de dónde, y no está en el teléfono. */
    AUSENTE,

    /** Encolado: el trabajo existe y el sistema lo corre cuando haya wifi. */
    ESPERANDO_WIFI,

    /** Bajando ahora. */
    DESCARGANDO,

    /** Se cortó, y **lo bajado se conserva**: se reanuda, no se reinicia. */
    INTERRUMPIDA,

    /** Completo y verificado. Funciona sin señal. */
    LISTA
}

/**
 * Un renglón de la sección: qué es, cuánto ocupa y en qué punto está.
 *
 * [megas] es **nulo cuando no hay paquete que anunciar** — el caso real del
 * mapa hoy. Un "0 MB" o un "—" en su lugar serían un número inventado, y el
 * principio 1 del brief dice que todo número sale del código.
 */
data class FilaDeDescarga(
    val cual: DescargaOpcional,
    val megas: String?,
    val estado: EstadoDeLaDescarga
)

/**
 * El renglón del dictado.
 *
 * El peso sale de [ModeloDeDictado.tamanoBytes] a través de la MISMA función
 * `megas` que usa la pantalla de descarga, así que la sección y la pantalla no
 * pueden anunciar números distintos. Son megabytes decimales (10⁶), la unidad
 * que el repo ya eligió en `:core:appgate`.
 */
fun filaDelDictado(modelo: ModeloDeDictado, estado: EstadoDelModelo): FilaDeDescarga =
    FilaDeDescarga(
        cual = DescargaOpcional.DICTADO,
        megas = megasDelDictado(modelo.tamanoBytes),
        estado = when (estado) {
            EstadoDelModelo.Ausente -> EstadoDeLaDescarga.AUSENTE
            EstadoDelModelo.EsperandoWifi -> EstadoDeLaDescarga.ESPERANDO_WIFI
            is EstadoDelModelo.Descargando -> EstadoDeLaDescarga.DESCARGANDO
            is EstadoDelModelo.Interrumpido -> EstadoDeLaDescarga.INTERRUMPIDA
            EstadoDelModelo.Listo -> EstadoDeLaDescarga.LISTA
        }
    )

/**
 * El renglón del mapa.
 *
 * Sin [paquete] no hay peso **y no hay origen**: los dos hechos vienen del
 * mismo `null` de `MapasModule.extracto()`, y por eso se derivan juntos en vez
 * de dejar que la sección los combine por su cuenta. El estado se impone sobre
 * el del puerto porque un paquete nulo no puede estar bajándose.
 */
fun filaDelMapa(paquete: ExtractoDeMapa?, estado: EstadoDelExtracto): FilaDeDescarga =
    FilaDeDescarga(
        cual = DescargaOpcional.MAPA,
        megas = paquete?.let { megasDelMapa(it.tamanoBytes) },
        estado = if (paquete == null) {
            EstadoDeLaDescarga.SIN_ORIGEN
        } else {
            when (estado) {
                EstadoDelExtracto.SinOrigen -> EstadoDeLaDescarga.SIN_ORIGEN
                EstadoDelExtracto.Ausente -> EstadoDeLaDescarga.AUSENTE
                EstadoDelExtracto.EsperandoWifi -> EstadoDeLaDescarga.ESPERANDO_WIFI
                is EstadoDelExtracto.Descargando -> EstadoDeLaDescarga.DESCARGANDO
                is EstadoDelExtracto.Interrumpido -> EstadoDeLaDescarga.INTERRUMPIDA
                is EstadoDelExtracto.Listo -> EstadoDeLaDescarga.LISTA
            }
        }
    )
