package com.example.msp_app.core.datos

import okhttp3.HttpUrl

/**
 * Convierte una petición en una clave sin datos personales: `MÉTODO /ruta`.
 *
 * Reglas (probadas en `NormalizadorDeRutasTest`): sin host, sin query string, sin
 * fragmento. **LISTA BLANCA**: solo se conserva un segmento si es la versión (`v2`) o un
 * literal de [LITERALES] (los de las rutas de la app); todo lo demás —números, UUID,
 * nombres con espacios o acentos, correos, cualquier palabra que no esté en la lista— es
 * `{id}`. `pathSegments` ya viene decodificado, así que una regla sobre `%` no veía nada
 * (hallazgo H5 de la revisión independiente, 2026-11-10). La ruta se corta en
 * [MAX_SEGMENTOS] segmentos.
 *
 * Costo asumido: una ruta nueva de la app cae en `{id}` (y su servicio en "Otros") hasta
 * que se agregue su literal aquí; es preferible a filtrar un nombre.
 */
object NormalizadorDeRutas {
    const val ID = "{id}"
    private const val MAX_SEGMENTOS = 8
    private val VERSION = Regex("^v[0-9]+$")

    /** Literales de las rutas de la app (Retrofit `@GET/@POST/...` y los dos streams SSE), en minúsculas. */
    val LITERALES: Set<String> = setOf(
        "cobranza", "pagos", "sync", "ventas", "saldos", "zona", "digest", "ids", "by-ids", "stream",
        "visitas", "capacidades", "tareas", "ubicacion", "cliente", "lineas", "usuarios",
        "ensure-vendedores-by-email", "agenda-compromisos", "conciliacion", "ciudades", "clientes",
        "traspasos", "costos", "garantias", "nueva", "eventos", "ventas-locales", "add-pago",
        "getallventasbyzona", "zonas-cliente", "almacenes", "imagenes", "articulos", "ping", "maps",
        "api", "geocode", "json"
    )

    fun clave(metodo: String, url: HttpUrl): String {
        val segmentos = url.pathSegments.filter { it.isNotEmpty() }
            .take(MAX_SEGMENTOS)
            .map(::normalizarSegmento)
        return metodo.uppercase() + " /" + segmentos.joinToString("/")
    }

    private fun normalizarSegmento(s: String): String =
        if (VERSION.matches(s) || s.lowercase() in LITERALES) s else ID
}
