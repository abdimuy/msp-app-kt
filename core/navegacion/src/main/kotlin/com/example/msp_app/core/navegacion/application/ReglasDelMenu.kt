package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import java.text.Normalizer

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

    /** Spec §2.1.3: el buscador aparece con 10 opciones o más. */
    const val MINIMO_PARA_BUSCAR: Int = 10

    private val MARCAS_DIACRITICAS = Regex("\\p{M}+")
    private val ESPACIOS = Regex("\\s+")

    fun totalDeOpciones(secciones: List<SeccionDelMenu>): Int = secciones.sumOf { it.items.size }

    fun muestraBuscador(secciones: List<SeccionDelMenu>): Boolean =
        totalDeOpciones(secciones) >= MINIMO_PARA_BUSCAR

    fun estaAbierta(
        seccionId: String,
        plegadas: Set<String>,
        activa: String?,
        tocadas: Set<String>,
        consulta: String
    ): Boolean = when {
        consulta.isNotBlank() -> true
        seccionId == activa && seccionId !in tocadas -> true
        else -> seccionId !in plegadas
    }

    fun filtrar(secciones: List<SeccionDelMenu>, consulta: String): List<SeccionDelMenu> {
        val buscada = normalizar(consulta)
        if (buscada.isEmpty()) return secciones
        return secciones
            .map { seccion ->
                seccion.copy(
                    items = seccion.items.filter {
                        normalizar(it.etiqueta).contains(buscada)
                    }
                )
            }
            .filter { it.items.isNotEmpty() }
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

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
            .replace(MARCAS_DIACRITICAS, "")
            .lowercase(BUSINESS_LOCALE)
}
