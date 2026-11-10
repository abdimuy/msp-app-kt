package com.example.msp_app.core.datos

import okhttp3.HttpUrl

/**
 * Convierte una petición en una clave sin datos personales: `MÉTODO /ruta`
 * con todo segmento que parezca un identificador reemplazado por `{id}`.
 *
 * Reglas (probadas en `NormalizadorDeRutasTest`): sin host, sin query string,
 * sin fragmento; un segmento es `{id}` si tiene cualquier dígito (salvo
 * `v1`/`v2`…), si es un UUID, si tiene `@` o si mide más de [MAX_SEGMENTO].
 * La ruta se corta en [MAX_SEGMENTOS] segmentos.
 */
object NormalizadorDeRutas {
    const val ID = "{id}"
    const val SUFIJO_FOTO = "#foto"
    private const val MAX_SEGMENTOS = 8
    private const val MAX_SEGMENTO = 24
    private val VERSION = Regex("^v[0-9]+$")

    fun clave(metodo: String, url: HttpUrl, multipart: Boolean): String {
        val segmentos = url.pathSegments.filter { it.isNotEmpty() }
            .take(MAX_SEGMENTOS)
            .map(::normalizarSegmento)
        val ruta = "/" + segmentos.joinToString("/")
        return metodo.uppercase() + " " + ruta + if (multipart) SUFIJO_FOTO else ""
    }

    private fun normalizarSegmento(s: String): String = when {
        VERSION.matches(s) -> s
        s.length > MAX_SEGMENTO -> ID
        s.any { it.isDigit() } -> ID
        '@' in s || '%' in s -> ID
        else -> s
    }
}
