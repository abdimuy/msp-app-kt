package com.example.msp_app.core.utils

import com.example.msp_app.data.models.sale.SaleWithProducts
import me.xdrop.fuzzywuzzy.FuzzySearch


fun searchSimilarItems(
    query: String,
    items: List<SaleWithProducts>,
    threshold: Int = 60
): List<SaleWithProducts> {
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
