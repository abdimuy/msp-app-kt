package com.example.msp_app.core.cache

/**
 * Wrapper para datos cacheados con metadata
 */
data class CacheWrapper<T>(
    val data: T,
    val metadata: CacheMetadata
)

/**
 * Resultado de operación de cache
 */
sealed class CacheResult<out T> {
    /**
     * Cache válido con datos frescos
     */
    data class Success<T>(
        val data: T,
        val metadata: CacheMetadata
    ) : CacheResult<T>()

    /**
     * Cache expirado pero con datos disponibles
     */
    data class Expired<T>(
        val data: T,
        val metadata: CacheMetadata
    ) : CacheResult<T>()

    /**
     * Cache vacío - no hay datos
     */
    data object Empty : CacheResult<Nothing>()

    /**
     * Error al leer/escribir cache
     */
    data class Error(val exception: Throwable) : CacheResult<Nothing>()

    /**
     * Obtiene los datos o null si no hay
     */
    fun getDataOrNull(): T? = when (this) {
        is Success -> data
        is Expired -> data
        else -> null
    }

    /**
     * Verifica si el cache es válido (no expirado)
     */
    fun isValid(): Boolean = this is Success

    /**
     * Verifica si hay datos disponibles (válidos o expirados)
     */
    fun hasData(): Boolean = this is Success || this is Expired

    /**
     * Obtiene la metadata o null
     */
    fun getMetadataOrNull(): CacheMetadata? = when (this) {
        is Success -> metadata
        is Expired -> metadata
        else -> null
    }
}
