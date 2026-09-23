package com.example.msp_app.core.utils

import java.text.Normalizer

/**
 * Fuzzy search utility to find similar items based on a query string.
 *
 * @param query The search query string.
 * @param items The list of items to search through.
 * @param threshold The minimum score (0-100) for an item to be considered a match.
 * @param selectText A function to extract the text from each item for comparison.
 * @return A list of items that match the query above the threshold, sorted by relevance.
 */
fun <T> searchSimilarItems(
    query: String,
    items: List<T>,
    threshold: Int = 60,
    selectText: (T) -> String
): List<T> {
    val cleanQuery = normalizeForSearch(query)
    if (cleanQuery.isEmpty()) return emptyList()

    val normalizedItems = items.map { item ->
        normalizeForSearch(selectText(item)) to item
    }
    val qLen = cleanQuery.length

    return normalizedItems
        .mapNotNull { (text, item) ->
            val matchLen = longestCommonSubstring(cleanQuery, text)
            val baseScore = matchLen * 100 / qLen
            if (baseScore >= threshold) {
                val positionBonus = calculatePositionBonus(cleanQuery, text)
                val finalScore = baseScore + positionBonus
                item to finalScore
            } else {
                null
            }
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

/**
 * Normalización única para búsqueda "tolerante a error de captura": minúsculas, sin
 * marcas de acento y con espacios colapsados a uno solo (sin importar cuántos de más
 * haya al inicio, al final o entre palabras). Es la ÚNICA función de normalización para
 * el buscador de clientes — la usan tanto [searchSimilarItems] (para el ranking) como
 * `ClienteRepository.searchClientes` y `ClienteRepository.syncFromServer` (para la
 * columna `NOMBRE_NORMALIZADO` y la consulta SQL). El defecto clásico es normalizar la
 * consulta pero no el dato (o viceversa); tenerla en un solo lugar hace que ese defecto
 * ya no se pueda escribir por accidente.
 *
 * ## Decisión: la "ñ" cuenta como "n" para BUSCAR
 *
 * La descomposición NFD de Unicode ya hace este trabajo sin código extra: "ñ" (U+00F1)
 * se descompone en "n" (U+006E) + tilde combinante (U+0303), y esa tilde es una marca
 * que este normalizador descarta igual que el acento de "é" o "á". El resultado es que
 * "pena" encuentra "PEÑA" — decisión deliberada, no un descuido: el cobrador teclea
 * rápido y muchas veces con el teclado del teléfono en inglés, donde la "ñ" no está a
 * un toque directo. Es seguro porque esta función solo se usa para COMPARAR: el nombre
 * que se guarda (`NOMBRE`) y el que se muestra en la lista de resultados conservan la
 * "ñ" real tal como vino de Microsip — normalizar para buscar nunca normaliza para
 * mostrar ni para guardar.
 */
fun normalizeForSearch(text: String): String {
    val nfd = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
    val withoutAccents = nfd.replace(DIACRITIC_MARKS, "")
    return withoutAccents.replace(MULTIPLE_SPACES, " ").trim()
}

private val DIACRITIC_MARKS = "\\p{M}".toRegex()
private val MULTIPLE_SPACES = "\\s+".toRegex()

private fun longestCommonSubstring(s1: String, s2: String): Int {
    val mod = 1_000_000_007L
    val base = 91138233L

    fun hasCommon(len: Int): Boolean {
        if (len == 0) return true
        var hash1 = 0L
        var power = 1L
        for (i in 0 until len) {
            hash1 = (hash1 * base + s1[i].code) % mod
            power = (power * base) % mod
        }
        val seen = mutableMapOf<Long, MutableList<Int>>().apply {
            put(hash1, mutableListOf(0))
        }
        for (i in len until s1.length) {
            hash1 = ((hash1 * base - s1[i - len].code * power % mod + mod) + s1[i].code) % mod
            seen.computeIfAbsent(hash1) { mutableListOf() }.add(i - len + 1)
        }

        var hash2 = 0L
        for (i in 0 until len) {
            hash2 = (hash2 * base + s2[i].code) % mod
        }
        seen[hash2]?.let { starts ->
            if (starts.any { s1.substring(it, it + len) == s2.substring(0, len) }) return true
        }
        for (i in len until s2.length) {
            hash2 = ((hash2 * base - s2[i - len].code * power % mod + mod) + s2[i].code) % mod
            seen[hash2]?.let { starts ->
                if (starts.any {
                        s1.substring(it, it + len) == s2.substring(
                            i - len + 1,
                            i + 1
                        )
                    }
                ) {
                    return true
                }
            }
        }
        return false
    }

    var low = 0
    var high = minOf(s1.length, s2.length) + 1
    while (low + 1 < high) {
        val mid = (low + high) / 2
        if (hasCommon(mid)) low = mid else high = mid
    }
    return low
}

private fun calculatePositionBonus(query: String, text: String): Int {
    if (query.isEmpty() || text.isEmpty()) return 0

    if (text.startsWith(query)) {
        return 50 // Bonus alto para coincidencia exacta al principio
    }

    val words = text.split(" ", "-", "_", ".")
    for (word in words) {
        if (word.startsWith(query)) {
            return 30 // Bonus medio para coincidencia al principio de palabra
        }
    }

    val position = text.indexOf(query)
    if (position >= 0) {
        val maxBonus = 20
        val positionPenalty = (position.toDouble() / text.length) * maxBonus
        return (maxBonus - positionPenalty).toInt()
    }

    return 0
}
