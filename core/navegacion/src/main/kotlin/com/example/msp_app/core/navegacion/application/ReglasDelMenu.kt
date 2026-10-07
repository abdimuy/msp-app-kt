package com.example.msp_app.core.navegacion.application

/**
 * Las reglas del menú que no necesitan Compose para probarse.
 *
 * ## Dos señales, no una bandera (spec §7, "doble escritura")
 *
 * [estaAbierta] mezcla la PREFERENCIA guardada ([plegadas]) con el
 * "forzado abierto" de la pantalla actual ([activa]) sin escribir nunca la
 * segunda en la primera. [tocadas] son las secciones que el usuario tocó en
 * ESTA apertura: si toca la sección activa, manda su preferencia; al cerrar el
 * menú [tocadas] se vacía y la próxima apertura vuelve a forzarla.
 */
object ReglasDelMenu {

    private val ESPACIOS = Regex("\\s+")

    fun estaAbierta(
        seccionId: String,
        plegadas: Set<String>,
        activa: String?,
        tocadas: Set<String>
    ): Boolean = when {
        seccionId == activa && seccionId !in tocadas -> true
        else -> seccionId !in plegadas
    }

    /** Las iniciales de las dos primeras palabras; "?" si no hay nombre. */
    fun iniciales(nombre: String): String = nombre
        .trim()
        .split(ESPACIOS)
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
}
