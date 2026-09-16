package com.example.msp_app.core.mapas.domain

/**
 * **El estilo del mapa, armado a mano y sin etiquetas.**
 *
 * ## Por qué no hay nombres de calle
 *
 * Una capa `symbol` con texto obliga a declarar `glyphs`, que es **una URL a un
 * servidor de tipografías**. Eso rompería de raíz la promesa entera de este
 * módulo: "funciona sin señal, siempre". Entre un mapa mudo que funciona en un
 * patio sin datos y uno con nombres que se queda en blanco, gana el mudo — y en
 * un cuadro de 130 dp los nombres tampoco se leerían.
 *
 * Por lo mismo no hay `sprite`: los iconos de POI también viven detrás de una
 * URL.
 *
 * ## Las capas salen del propio extracto
 *
 * `earth`, `landuse`, `water`, `buildings`, `roads` son cinco de las nueve
 * `vector_layers` que el archivo declara en sus metadatos (medido sobre
 * `ruta-cobranza-z14.pmtiles`: `boundaries, buildings, earth, landcover,
 * landuse, places, pois, roads, water`). Las otras cuatro no se pintan: dos son
 * de etiquetas (`places`, `pois`), `landcover` muere en z7 y `boundaries` no
 * dice nada dentro de una colonia.
 *
 * `maxzoom` de la fuente sale de [MapaDeLaRuta.cabecera] y no de una constante:
 * si alguien regenera el extracto a z15, el estilo lo respeta solo. Sin eso
 * MapLibre pediría teselas que el archivo no tiene.
 */
fun estiloDeLaRuta(mapa: MapaDeLaRuta, paleta: PaletaDelMapa): String {
    val cab = mapa.cabecera
    return """
        {
          "version": 8,
          "name": "ruta-cobranza",
          "sources": {
            "$FUENTE": {
              "type": "vector",
              "url": "${mapa.urlDeTeselas.enJson()}",
              "minzoom": ${cab.zoomMinimo},
              "maxzoom": ${cab.zoomMaximo},
              "attribution": "${ATRIBUCION_DE_OSM.enJson()}"
            }
          },
          "layers": [
            ${capaDeFondo(paleta.tierra)},
            ${capaDeRelleno("tierra", "earth", paleta.tierra)},
            ${capaDeRelleno("suelo", "landuse", paleta.suelo)},
            ${capaDeRelleno("agua", "water", paleta.agua)},
            ${capaDeRelleno("edificios", "buildings", paleta.edificios)},
            ${capaDeCalles(paleta.calles)}
          ]
        }
    """.trimIndent()
}

/**
 * **La atribución que ODbL exige.**
 *
 * No es un texto elegido: es el que el propio extracto trae en sus metadatos
 * (`attribution` del JSON interno de `ruta-cobranza-z14.pmtiles`, medido el
 * 2026-09-16: `<a href="https://www.openstreetmap.org/copyright">&copy;
 * OpenStreetMap</a>`), sin el HTML. `AtribucionDeOpenStreetMapTest` compara
 * esta constante contra el archivo real cuando el archivo está disponible: si
 * alguien la cambia por su cuenta, se pone roja.
 */
const val ATRIBUCION_DE_OSM: String = "© OpenStreetMap"

/** El id de la única fuente del estilo. */
private const val FUENTE = "protomaps"

private fun capaDeFondo(color: String) =
    """{"id":"fondo","type":"background","paint":{"background-color":"${color.enJson()}"}}"""

private fun capaDeRelleno(id: String, capaDelArchivo: String, color: String) =
    """{"id":"${id.enJson()}","type":"fill","source":"$FUENTE",""" +
        """"source-layer":"${capaDelArchivo.enJson()}",""" +
        """"paint":{"fill-color":"${color.enJson()}"}}"""

/**
 * Las calles, con el ancho creciendo con el zoom.
 *
 * Una línea de ancho fijo se ve como un pelo a z12 y como una autopista a z16.
 * La interpolación es lo que hace que la traza se lea igual en el cuadro de 130
 * dp del detalle que en un mapa a pantalla completa.
 */
private fun capaDeCalles(color: String) =
    """{"id":"calles","type":"line","source":"$FUENTE","source-layer":"roads",""" +
        """"paint":{"line-color":"${color.enJson()}","line-opacity":0.55,""" +
        """"line-width":["interpolate",["exponential",1.5],["zoom"],10,0.4,14,3.0,18,12.0]}}"""

/**
 * Escapa lo que va dentro de una cadena JSON. La ruta del archivo viene del
 * sistema de archivos del teléfono y no de una constante: una comilla o una
 * contrabarra ahí adentro romperían el estilo entero, y MapLibre no diría por
 * qué.
 */
private fun String.enJson(): String = buildString {
    this@enJson.forEach { c ->
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
}
