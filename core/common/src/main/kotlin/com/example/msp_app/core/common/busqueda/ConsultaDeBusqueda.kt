package com.example.msp_app.core.common.busqueda

/**
 * Lo que el usuario tecleó, ya interpretado (E-APP-157). Cuatro formas, ninguna lanza:
 *
 * - [Vacia]: nada que buscar (menos de 2 caracteres útiles, solo espacios o puntuación).
 * - [PorNumero]: solo dígitos (con espacios o puntuación entre ellos) → número de cliente.
 * - [PorNombre]: el resto.
 */
sealed interface ConsultaDeBusqueda {

    data object Vacia : ConsultaDeBusqueda

    /** [digitos] sin ceros a la izquierda; nunca vacío. */
    data class PorNumero(val digitos: String) : ConsultaDeBusqueda

    /**
     * [normalizado] es el texto completo normalizado (sirve para el "nombre exacto").
     * [palabras] son las que van a la consulta SQL: como mucho [MAXIMO_DE_PALABRAS], porque
     * cada una es un argumento ligado y Android ≤ 11 tope en 999 variables por consulta.
     */
    data class PorNombre(val normalizado: String, val palabras: List<String>) : ConsultaDeBusqueda

    companion object {
        const val MINIMO_DE_CARACTERES = 2
        const val MAXIMO_DE_PALABRAS = 8

        fun interpretar(texto: String): ConsultaDeBusqueda {
            val normalizado = NormalizadorDeBusqueda.normalizar(texto)
            val todas = NormalizadorDeBusqueda.palabras(normalizado)
            return when {
                todas.isEmpty() -> Vacia
                todas.all { palabra -> palabra.all { it in '0'..'9' } } -> numero(todas)
                // Al menos una palabra con 2 o más caracteres: "a b" son dos letras sueltas, ruido.
                todas.none { it.length >= MINIMO_DE_CARACTERES } -> Vacia
                else -> PorNombre(normalizado, todas.take(MAXIMO_DE_PALABRAS))
            }
        }

        private fun numero(palabras: List<String>): ConsultaDeBusqueda {
            val juntos = palabras.joinToString("")
            if (juntos.length < MINIMO_DE_CARACTERES) return Vacia
            val sinCeros = juntos.trimStart('0')
            return if (sinCeros.isEmpty()) Vacia else PorNumero(sinCeros)
        }
    }
}
