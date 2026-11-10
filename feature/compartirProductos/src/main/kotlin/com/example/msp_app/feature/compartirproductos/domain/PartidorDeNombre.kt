package com.example.msp_app.feature.compartirproductos.domain

/** El nombre de Microsip partido para la tarjeta: lo grande ([titulo]) y lo chico ([detalle]). */
data class TituloYDetalle(val titulo: String, val detalle: String?)

/**
 * Parte el nombre en título + detalle en el **primer** separador: ` con `
 * (donde `C/` ya se expandió a «con»), una coma, ` - ` o un paréntesis.
 *
 * El título siempre lleva al menos una palabra: un separador al principio del
 * nombre no parte nada. Si no hay separador, todo es título y no hay detalle.
 * En el detalle los paréntesis se quitan (`Sala (verde)` → detalle `Verde`).
 * El detalle sale en tipo oración si el original venía en mayúsculas.
 */
object PartidorDeNombre {

    private val SEPARADORES = listOf(
        Regex("\\s+con(?=\\s|$)", RegexOption.IGNORE_CASE),
        Regex(","),
        Regex("\\s+[-–—](?:\\s+|$)"),
        Regex("\\(")
    )

    fun partir(nombreOriginal: String): TituloYDetalle {
        val normalizado = NormalizadorDeNombre.normalizar(nombreOriginal)
        if (normalizado.isEmpty()) return TituloYDetalle("", null)
        val corte = SEPARADORES.mapNotNull { regla ->
            regla.findAll(normalizado).firstOrNull {
                cabeTitulo(normalizado, it)
            }
        }
            .minByOrNull { it.range.first }
            ?: return TituloYDetalle(normalizado, null)
        val titulo = normalizado.substring(
            0,
            corte.range.first
        ).trim().trimEnd(',', '-', '–', '—', ':', ' ')
        // En ` con ` la palabra «con» abre el detalle; en los demás separadores se descarta.
        val crudo = if (corte.value.trim().equals("con", ignoreCase = true)) {
            normalizado.substring(corte.range.first).trim()
        } else {
            normalizado.substring(corte.range.last + 1).trim()
        }
        val sinParentesis = crudo.replace(
            "(",
            ""
        ).replace(")", "").replace(Regex("[\\s\\u00A0]+"), " ").trim().trimStart(',', '-', ' ')
        val detalle = if (NormalizadorDeNombre.esMayusculas(nombreOriginal)) {
            NormalizadorDeNombre.aOracion(sinParentesis)
        } else {
            NormalizadorDeNombre.capitalizarPrimera(sinParentesis)
        }
        if (titulo.isEmpty()) return TituloYDetalle(normalizado, null)
        return TituloYDetalle(titulo, detalle.ifBlank { null })
    }

    private fun cabeTitulo(texto: String, hallazgo: MatchResult): Boolean =
        texto.substring(0, hallazgo.range.first).any { it.isLetterOrDigit() }
}
