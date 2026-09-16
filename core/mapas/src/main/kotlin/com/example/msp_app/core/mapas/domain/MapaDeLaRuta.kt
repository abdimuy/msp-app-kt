package com.example.msp_app.core.mapas.domain

/**
 * **El mapa que el teléfono ya tiene**: dónde está el archivo y qué cubre.
 *
 * Solo se construye después de que la cabecera se leyó y el archivo resultó
 * completo, así que tener una instancia de esto **es** tener un mapa dibujable.
 * No existe un `MapaDeLaRuta` de un archivo a medias: ése es el estado
 * [EstadoDelExtracto.Interrumpido], y no trae mapa.
 *
 * [ruta] es una ruta absoluta del sistema de archivos del teléfono. **Nunca
 * viaja a telemetría**: lleva el `filesDir` del dispositivo.
 */
data class MapaDeLaRuta(
    val ruta: String,
    val cabecera: CabeceraDePmtiles
) {
    init {
        require(ruta.isNotBlank()) { "un mapa sin ruta de archivo no se puede dibujar" }
    }

    /**
     * La URL que MapLibre entiende para leer teselas de un `.pmtiles` local.
     *
     * El esquema es `pmtiles://file://<ruta absoluta>` y está soportado de
     * forma nativa desde MapLibre Android 11.8.0 (PR #2882 del changelog; la
     * documentación del proyecto dice 11.7.0 — se resuelve 13.0.2, muy por
     * encima de las dos). `pmtiles://asset://` **no** sirve: `AssetManager` no
     * hace lecturas por rango y PMTiles las necesita para leer su cabecera.
     */
    val urlDeTeselas: String get() = "pmtiles://file://$ruta"

    /** ¿La caja del extracto contiene este punto? */
    fun cubre(punto: PuntoDelMapa): Boolean =
        punto.lat in cabecera.sur..cabecera.norte && punto.lng in cabecera.oeste..cabecera.este
}

/**
 * Un punto **medido** del mapa.
 *
 * Existe como un solo valor y no como dos `Double` sueltos por la misma razón
 * que `UbicacionDelCobro` en `:feature:pagos`: media coordenada no ubica nada.
 * Este tipo es el espejo de aquél en el lado del mapa — no se importa porque
 * `:core:mapas` no puede depender de un `:feature:*`, y porque el mapa no sabe
 * ni le importa que el punto venga de un abono.
 */
data class PuntoDelMapa(val lat: Double, val lng: Double) {
    init {
        require(lat in LATITUD_MINIMA..LATITUD_MAXIMA) { "latitud fuera del mundo" }
        require(lng in LONGITUD_MINIMA..LONGITUD_MAXIMA) { "longitud fuera del mundo" }
    }

    private companion object {
        const val LATITUD_MINIMA = -90.0
        const val LATITUD_MAXIMA = 90.0
        const val LONGITUD_MINIMA = -180.0
        const val LONGITUD_MAXIMA = 180.0
    }
}
