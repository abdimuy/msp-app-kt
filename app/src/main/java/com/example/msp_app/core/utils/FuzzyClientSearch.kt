package com.example.msp_app.core.utils

import me.xdrop.fuzzywuzzy.FuzzySearch


fun <T> searchSimilarItems(
    query: String,
    items: List<T>,
    threshold: Int = 60,
    selectText: (T) -> String
): List<T> {
    val cleanQuery = query.trim().lowercase()

    return items
        .mapNotNull { item ->
            val text = selectText(item).trim().lowercase()
            val score = FuzzySearch.partialRatio(cleanQuery, text)
            if (score >= threshold) item to score else null
        }
        .sortedByDescending { it.second }
        .map { it.first }
}