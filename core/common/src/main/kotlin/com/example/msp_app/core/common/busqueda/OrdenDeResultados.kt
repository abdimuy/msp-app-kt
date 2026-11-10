package com.example.msp_app.core.common.busqueda

/** Una fila candidata: el id del cliente y su `NOMBRE_NORMALIZADO` tal como está guardado. */
data class FilaDeBusqueda(val id: Int, val nombreNormalizado: String)

/**
 * Orden de los resultados del buscador de clientes (E-APP-157), función pura.
 *
 * Llaves, de la más fuerte a la más débil (menor es mejor):
 * 1. nombre exacto (todo el nombre = todo lo tecleado);
 * 2. todas las palabras tecleadas son INICIO de alguna palabra del nombre;
 * 3. las palabras aparecen en el nombre en el MISMO orden en que se teclearon;
 * 4. nombre más corto;
 * 5. alfabético;
 * 6. `CLIENTE_ID` (desempate estable: dos homónimos idénticos siempre salen en el mismo orden).
 *
 * El texto guardado se re-normaliza con las reglas nuevas ([NormalizadorDeBusqueda.normalizarGuardado])
 * para que un padrón guardado con la normalización vieja (con puntuación) se ordene igual.
 */
object OrdenDeResultados {

    internal class Clave(
        val exacto: Boolean,
        val iniciosDePalabra: Boolean,
        val mismoOrden: Boolean,
        val largo: Int,
        val nombre: String,
        val id: Int
    ) : Comparable<Clave> {
        override fun compareTo(other: Clave): Int = compareValuesBy(
            this,
            other,
            { !it.exacto },
            { !it.iniciosDePalabra },
            { !it.mismoOrden },
            { it.largo },
            { it.nombre },
            { it.id }
        )
    }

    fun ordenar(
        filas: List<FilaDeBusqueda>,
        consulta: ConsultaDeBusqueda.PorNombre
    ): List<FilaDeBusqueda> =
        filas.map { it to clave(it, consulta) }.sortedBy { it.second }.map { it.first }

    internal fun clave(fila: FilaDeBusqueda, consulta: ConsultaDeBusqueda.PorNombre): Clave {
        val nombre = NormalizadorDeBusqueda.normalizarGuardado(fila.nombreNormalizado)
        val tokens = NormalizadorDeBusqueda.palabras(nombre)
        val palabras = consulta.palabras
        return Clave(
            exacto = nombre == consulta.normalizado,
            iniciosDePalabra = palabras.all { palabra -> tokens.any { it.startsWith(palabra) } },
            mismoOrden = enElMismoOrden(nombre, palabras),
            largo = nombre.length,
            nombre = nombre,
            id = fila.id
        )
    }

    private fun enElMismoOrden(nombre: String, palabras: List<String>): Boolean {
        var desde = 0
        for (palabra in palabras) {
            val en = nombre.indexOf(palabra, desde)
            if (en < 0) return false
            desde = en + palabra.length
        }
        return true
    }
}
