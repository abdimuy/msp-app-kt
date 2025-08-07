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
    val cleanQuery = normalize(query)
    if (cleanQuery.isEmpty()) return emptyList()

    val normalizedItems = items.map { item ->
        normalize(selectText(item)) to item
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
            } else null
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

private fun normalize(text: String): String {
    val nfd = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
    return nfd.replace("\\p{M}".toRegex(), "")
}

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
                    }) return true
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