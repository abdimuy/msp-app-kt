package com.example.msp_app.core.common.busqueda

/**
 * Distancia de edición ≤ 1 (distancia de Damerau-Levenshtein "de alineación óptima"): una
 * sustitución, una omisión, una inserción o una TRANSPOSICIÓN de dos letras contiguas cuenta
 * como UN error — los cuatro errores de dedo típicos (E-APP-157).
 *
 * No calcula la distancia completa: decide si es 0 o 1 en una sola pasada, sin asignar
 * memoria, porque corre contra ~44,000 nombres cuando la búsqueda exacta no encontró nada.
 */
object DistanciaDeEdicion {

    fun dentroDeUno(a: CharSequence, b: CharSequence): Boolean = dentroDeUno(a, 0, a.length, b)

    /** Compara el tramo `a[desde, desde+largoA)` contra [b]. */
    fun dentroDeUno(a: CharSequence, desde: Int, largoA: Int, b: CharSequence): Boolean {
        val largoB = b.length
        if (kotlin.math.abs(largoA - largoB) > 1) return false
        val tope = minOf(largoA, largoB)
        var i = 0
        while (i < tope && a[desde + i] == b[i]) i++
        if (i == tope) return true // iguales, o uno es el otro con una letra de más al final
        return when {
            largoA == largoB -> sustitucionOTransposicion(a, desde, b, i, largoA)
            largoA > largoB -> iguales(a, desde + i + 1, b, i, largoA - i - 1)
            else -> iguales(a, desde + i, b, i + 1, largoB - i - 1)
        }
    }

    private fun sustitucionOTransposicion(
        a: CharSequence,
        desde: Int,
        b: CharSequence,
        i: Int,
        largo: Int
    ): Boolean {
        if (iguales(a, desde + i + 1, b, i + 1, largo - i - 1)) return true
        return i + 1 < largo &&
            a[desde + i] == b[i + 1] &&
            a[desde + i + 1] == b[i] &&
            iguales(a, desde + i + 2, b, i + 2, largo - i - 2)
    }

    private fun iguales(
        a: CharSequence,
        desdeA: Int,
        b: CharSequence,
        desdeB: Int,
        cuantos: Int
    ): Boolean {
        for (k in 0 until cuantos) if (a[desdeA + k] != b[desdeB + k]) return false
        return true
    }
}
