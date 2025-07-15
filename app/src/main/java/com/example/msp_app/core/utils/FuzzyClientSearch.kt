package com.example.msp_app.core.utils

import com.example.msp_app.data.models.sale.Sale
import me.xdrop.fuzzywuzzy.FuzzySearch

fun searchSimilarItems(
    query: String,
    items: List<Sale>,
    threshold: Int = 60
): List<Sale> {
    val cleanQuery = query.trim().lowercase()

    return items.mapNotNull { sale ->
        val client = sale.CLIENTE.trim().lowercase()
        val score = FuzzySearch.partialRatio(cleanQuery, client)
        if (score >= threshold) {
            sale to score
        } else {
            null
        }
    }
        .sortedByDescending { it.second }
        .map { it.first }
}
