package com.example.msp_app.core.cache

/**
 * Configuración del cache - permite personalizar comportamiento por entidad
 */
data class CacheConfig(
    val fileName: String,
    val ttlMillis: Long = CacheMetadata.DEFAULT_TTL,
    val version: Int = 1,
    val enableLogging: Boolean = true
) {
    companion object {
        /**
         * Configuración para datos que cambian frecuentemente (30 min TTL)
         */
        fun shortLived(fileName: String) = CacheConfig(
            fileName = fileName,
            ttlMillis = CacheMetadata.SHORT_TTL,
            version = 1
        )

        /**
         * Configuración estándar (24 horas TTL)
         */
        fun standard(fileName: String) = CacheConfig(
            fileName = fileName,
            ttlMillis = CacheMetadata.DEFAULT_TTL,
            version = 1
        )

        /**
         * Configuración para datos que cambian poco (7 días TTL)
         */
        fun longLived(fileName: String) = CacheConfig(
            fileName = fileName,
            ttlMillis = CacheMetadata.LONG_TTL,
            version = 1
        )
    }
}
