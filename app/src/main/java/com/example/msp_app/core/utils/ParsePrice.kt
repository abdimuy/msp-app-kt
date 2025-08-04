package com.example.msp_app.core.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

fun parsePrice(pricesStr: String?): Map<String, Double> {
    val priceMap = mutableMapOf<String, Double>()

    if (pricesStr.isNullOrBlank()) return priceMap

    val cleaned = pricesStr.trim().removePrefix("{").removeSuffix("}")
    val parts = cleaned.split(",")

    for (part in parts) {
        val keyValue = part.split(":")
        if (keyValue.size != 2) continue

        val rawKey = keyValue[0].trim()
        val rawValue = keyValue[1].trim()

        val key = rawKey.removeSurrounding("\"").removeSurrounding("'")
        val cleanedValue = rawValue.replace(",", ".").replace(Regex("[^0-9.]"), "")

        val number = cleanedValue.toDoubleOrNull()

        if (key.isNotBlank() && number != null) {
            priceMap[key] = number
        }
    }

    return priceMap
}

fun parsePriceJsonToMap(json: String?): Map<String, Double> {
    if (json.isNullOrBlank()) return emptyMap()
    val type = object : TypeToken<Map<String, Double>>() {}.type
    return Gson().fromJson(json, type)
}
