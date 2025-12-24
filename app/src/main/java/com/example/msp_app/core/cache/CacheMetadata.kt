package com.example.msp_app.core.cache

import com.google.gson.annotations.SerializedName

/**
 * Metadata del cache para versionado y expiración
 */
data class CacheMetadata(
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("version")
    val version: Int = 1,

    @SerializedName("ttlMillis")
    val ttlMillis: Long = DEFAULT_TTL,

    @SerializedName("source")
    val source: CacheSource = CacheSource.UNKNOWN
) {
    companion object {
        const val DEFAULT_TTL = 24 * 60 * 60 * 1000L // 24 horas
        const val SHORT_TTL = 30 * 60 * 1000L // 30 minutos
        const val LONG_TTL = 7 * 24 * 60 * 60 * 1000L // 7 días
    }

    fun isExpired(): Boolean {
        return System.currentTimeMillis() - timestamp > ttlMillis
    }

    fun age(): Long = System.currentTimeMillis() - timestamp

    fun ageFormatted(): String {
        val ageMillis = age()
        val seconds = ageMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days día${if (days > 1) "s" else ""}"
            hours > 0 -> "$hours hora${if (hours > 1) "s" else ""}"
            minutes > 0 -> "$minutes minuto${if (minutes > 1) "s" else ""}"
            else -> "Hace un momento"
        }
    }
}

enum class CacheSource {
    NETWORK,
    CACHE,
    UNKNOWN
}
