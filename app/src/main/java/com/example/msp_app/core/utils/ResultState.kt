package com.example.msp_app.core.utils

import com.example.msp_app.core.cache.CacheMetadata
import com.example.msp_app.core.cache.CacheSource

sealed class ResultState<out T> {
    object Idle : ResultState<Nothing>()
    object Loading : ResultState<Nothing>()
    data class Success<out T>(
        val data: T,
        val source: CacheSource = CacheSource.NETWORK,
        val metadata: CacheMetadata? = null
    ) : ResultState<T>()
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : ResultState<Nothing>()

    /**
     * Estado especial para datos offline
     * Indica que los datos vienen del cache local
     */
    data class Offline<out T>(
        val data: T,
        val metadata: CacheMetadata,
        val isExpired: Boolean = false
    ) : ResultState<T>()

    // Extension helpers
    fun isLoading() = this is Loading
    fun isSuccess() = this is Success
    fun isError() = this is Error
    fun isOffline() = this is Offline
    fun isIdle() = this is Idle

    /**
     * Obtiene los datos si están disponibles (Success u Offline)
     */
    fun dataOrNull(): T? = when (this) {
        is Success -> data
        is Offline -> data
        else -> null
    }

    /**
     * Obtiene el mensaje de error si es Error
     */
    fun errorOrNull(): String? = when (this) {
        is Error -> message
        else -> null
    }
}
