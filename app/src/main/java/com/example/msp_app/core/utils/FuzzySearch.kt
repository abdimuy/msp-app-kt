package com.example.msp_app.core.utils

/**
 * Utility object for string matching with accent normalization.
 */
object FuzzySearch {

    /**
     * Normalizes a string for search by removing accents and converting to lowercase.
     */
    fun normalize(text: String): String {
        return text.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
    }

    /**
     * Checks if the text contains the query (case and accent insensitive).
     */
    fun matches(text: String, query: String): Boolean {
        if (query.isBlank()) return true
        return normalize(text).contains(normalize(query))
    }
}

/**
 * Extension function for convenient matching.
 */
fun String.matchesFuzzy(query: String): Boolean = FuzzySearch.matches(this, query)
