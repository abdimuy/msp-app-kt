package com.example.msp_app.feature.configuracion.ui

import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.speech.domain.megas as megasDelDictado

/**
 * **La descarga opcional, en el vocabulario de la sección que la pinta.**
 *
 * `:core:speech` tiene su propio `EstadoDelModelo`, con el vocabulario de quien
 * baja un archivo. La sección de Configuración pinta **renglones**, y un
 * renglón necesita el vocabulario del cobrador. Éste es ese vocabulario, y vive
 * acá —del lado del que pinta— y no dentro del módulo: `:core:speech` no tiene
 * por qué saber cómo se dibuja una sección de Configuración.
 *
 * ## Hubo un segundo renglón, y por qué ya no está
 *
 * El extracto de calles de `:core:mapas` era el otro. Ese módulo se fue entero:
 * su renderizador (MapLibre) pesaba 47.9 MB de `.so` repartidos en cuatro ABIs y
 * viajaba en el APK **aunque nadie bajara las teselas** — peso obligatorio por
 * una función opcional, en una app que se reparte por descarga directa. Sin
 * renderizador el `.pmtiles` no tiene consumidor, así que no hay qué ofrecer.
 * "Cómo llegar" abre la app de mapas del teléfono, que además navega mejor.
 *
 * Con él se fue también el estado `SIN_ORIGEN`: era suyo —el `.pmtiles` nunca
 * estuvo publicado en ningún servidor— y el dictado no puede caer ahí, porque su
 * URL es una constante del módulo.
 */
enum class DescargaOpcional {

    /** El modelo de alta fidelidad de `:core:speech`. */
    DICTADO
}

/** En qué punto está una descarga opcional. */
enum class EstadoDeLaDescarga {

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
 * [megas] **no es nulo**: el peso sale del paquete que el módulo anuncia, y un
 * módulo que no anuncia su paquete no tiene renglón que pintar. Fue nulo
 * mientras existió el renglón del mapa, que no tenía paquete del cual sacarlo.
 */
data class FilaDeDescarga(
    val cual: DescargaOpcional,
    val megas: String,
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
