package com.example.msp_app.core.common.busqueda

import java.text.Normalizer

/**
 * Normalización del buscador de clientes (E-APP-157): minúsculas, sin acentos ni diéresis
 * (la Ñ cuenta como N), sin puntuación y con los espacios colapsados.
 *
 * ## Compatibilidad con lo que ya está guardado en los teléfonos (E-APP-159)
 *
 * La columna `NOMBRE_NORMALIZADO` de los teléfonos se llenó con la normalización VIEJA
 * (`normalizeForSearch`: minúsculas, sin acentos, espacios colapsados, **con** la puntuación
 * original) y no se vuelve a llenar hasta el próximo sync. Esta función es compatible con ese
 * dato por construcción: la puntuación se convierte en ESPACIO, así que toda palabra nueva es
 * una racha contigua del texto viejo y la consulta SQL por palabras (un `LIKE` por palabra,
 * unidas con AND) sigue encontrando filas viejas sin migración. El orden de los resultados se
 * calcula re-normalizando el texto guardado con [normalizarGuardado], de modo que las reglas
 * nuevas se aplican "al vuelo" a los dos lados.
 *
 * Puntuación que se ignora (se vuelve espacio): `. , - – — ' ’ ‘ " “ ” ` ´ ; : ! ? ¿ ¡ ( ) [ ] /`.
 * `% _ \` NO se ignoran: son caracteres literales aquí y se escapan en el `LIKE`
 * ([escaparParaLike]), de modo que no actúan como comodines.
 */
object NormalizadorDeBusqueda {

    private const val SEPARADORES = ".,-–—'’‘\"“”`´;:!?¿¡()[]/"

    /** Texto tecleado por el usuario → forma comparable. Nunca lanza, sea cual sea la entrada. */
    fun normalizar(texto: String): String {
        val descompuesto = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
        return limpiar(descompuesto)
    }

    /**
     * Texto ya guardado en `NOMBRE_NORMALIZADO` (con la normalización vieja o la nueva) →
     * forma comparable. Camino rápido: si ya es ASCII no hace falta descomponer Unicode.
     */
    fun normalizarGuardado(texto: String): String {
        if (texto.all { it.code < ASCII_LIMITE }) return limpiar(texto.lowercase())
        return normalizar(texto)
    }

    /** Palabras de una forma ya normalizada. */
    fun palabras(normalizado: String): List<String> =
        if (normalizado.isEmpty()) emptyList() else normalizado.split(' ')

    /**
     * Escapa `\`, `%` y `_` para usarlo dentro de `LIKE ? ESCAPE '\'`. Va siempre como
     * ARGUMENTO ligado, nunca concatenado en el texto de la consulta.
     */
    fun escaparParaLike(palabra: String): String {
        val salida = StringBuilder(palabra.length * 2)
        for (c in palabra) {
            if (c == '\\' || c == '%' || c == '_') salida.append('\\')
            salida.append(c)
        }
        return salida.toString()
    }

    private fun limpiar(texto: String): String {
        val salida = StringBuilder(texto.length)
        var pendienteDeEspacio = false
        for (c in texto) {
            when {
                esMarcaCombinante(c) || esInvisible(c) -> Unit
                c in SEPARADORES || c.isWhitespace() || Character.isISOControl(c) ->
                    pendienteDeEspacio = salida.isNotEmpty()
                else -> {
                    if (pendienteDeEspacio) salida.append(' ')
                    pendienteDeEspacio = false
                    salida.append(c)
                }
            }
        }
        return salida.toString()
    }

    private fun esMarcaCombinante(c: Char): Boolean {
        val tipo = Character.getType(c)
        return tipo == Character.NON_SPACING_MARK.toInt() ||
            tipo == Character.COMBINING_SPACING_MARK.toInt() ||
            tipo == Character.ENCLOSING_MARK.toInt()
    }

    /** Caracteres de formato sin ancho (p. ej. U+200B): no deben partir ni pegar palabras. */
    private fun esInvisible(c: Char): Boolean = Character.getType(c) == Character.FORMAT.toInt()

    private const val ASCII_LIMITE = 128
}
