package com.example.msp_app.feature.pagos.domain

import java.text.Normalizer

/**
 * La búsqueda de la lista: sobre el CLIENTE, no sobre la venta.
 *
 * ## Qué se busca
 *
 * Los mismos cinco datos que la pantalla vieja ya concatenaba
 * (`SalesScreen.kt:68`): nombre, folio, calle, ciudad y teléfono. La diferencia
 * es que aquí se concatenan **por cliente** —los folios de TODAS sus ventas
 * entran al mismo texto— para que teclear el folio de la segunda venta traiga
 * al cliente, no a una fila suelta que parece otra persona.
 *
 * ## Por qué no se reusa `searchSimilarItems`
 *
 * Porque vive en `:app` (`core/utils/FuzzyClientSearch.kt`) y `:feature:pagos`
 * no puede depender de `:app` — la dirección de dependencias del contrato
 * hexagonal solo va al revés. Verificado con
 * `grep -rn "searchSimilarItems" --include='*.kt'`, la misma consulta que SÍ lo
 * encontró en `:app` (control positivo): sus tres consumidores son
 * `SalesScreen`, `ProductsCatalogScreen` y `ClienteRepository`, los tres en
 * `:app`.
 *
 * Subirlo a `:core:common` sería mudar código de `:app` con sus tres
 * consumidores detrás, que es una migración con su propia auditoría y no lo que
 * esta tarea vino a hacer.
 *
 * ## Y por qué la sustituta no pierde resultados
 *
 * La pantalla vieja llamaba a `searchSimilarItems(..., threshold = 90)`. Ese
 * umbral exige que la subcadena común más larga cubra >=90% de la consulta:
 * para "hidalgo" (7 letras) hacen falta 7 letras seguidas —6 dan 85 y quedan
 * fuera—, o sea, exactamente contención de subcadena. Para cualquier consulta
 * de hasta 10 caracteres, "contiene" y "fuzzy al 90%" son el mismo conjunto de
 * resultados, y este es determinista además de barato por tecla.
 *
 * Lo que sí se conserva tal cual es la normalización: minúsculas y sin acentos
 * (NFD + quitar marcas), para que "maria" encuentre a "María".
 */
object BusquedaDeClientes {

    /**
     * El texto sobre el que se busca, ya normalizado. Se construye UNA vez al
     * armar la lista (`ReunirCartera`) y no en cada tecla.
     */
    fun textoBuscable(campos: List<String>): String =
        normalizar(campos.filter { it.isNotBlank() }.joinToString(SEPARADOR))

    /** ¿[textoBuscable] contiene lo que se tecleó? Consulta en blanco = no filtra. */
    fun coincide(textoBuscable: String, query: String): Boolean {
        val buscado = normalizar(query)
        return buscado.isEmpty() || textoBuscable.contains(buscado)
    }

    /** Minúsculas y sin acentos — misma receta que `FuzzyClientSearch.normalize`. */
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
            .replace(MARCAS_DE_ACENTO, "")

    /** Separa los campos concatenados; un espacio, como la pantalla vieja. */
    private const val SEPARADOR = " "

    private val MARCAS_DE_ACENTO = "\\p{M}".toRegex()
}
