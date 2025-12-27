package com.example.msp_app.core.sync

/**
 * Tipos de conflicto que pueden ocurrir durante la sincronización
 */
enum class ConflictType {
    /** Stock insuficiente */
    INSUFFICIENT_STOCK,

    /** Entidad ya existe (duplicado) */
    DUPLICATE,

    /** Entidad fue modificada en el servidor */
    CONCURRENT_MODIFICATION,

    /** Entidad ya no existe en el servidor */
    NOT_FOUND,

    /** Otro tipo de conflicto */
    OTHER
}

/**
 * Resultado de una operación de sincronización
 */
sealed class SyncResult<out T> {

    /** Sincronización exitosa */
    data class Success<T>(
        val data: T,
        val serverResponse: Any? = null
    ) : SyncResult<T>()

    /** Error que permite reintentar */
    data class RetryableError(
        val message: String,
        val exception: Throwable? = null,
        val httpCode: Int? = null
    ) : SyncResult<Nothing>()

    /** Error permanente, no reintentar */
    data class PermanentError(
        val message: String,
        val errorCode: String,
        val exception: Throwable? = null,
        val httpCode: Int? = null,
        val details: Map<String, Any>? = null
    ) : SyncResult<Nothing>()

    /** Conflicto - requiere intervención del usuario */
    data class Conflict(
        val message: String,
        val conflictType: ConflictType,
        val details: Map<String, Any>? = null
    ) : SyncResult<Nothing>()

    /** Operación cancelada */
    data object Cancelled : SyncResult<Nothing>()

    /**
     * Verifica si el resultado es exitoso
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Verifica si hay un error
     */
    fun isError(): Boolean = this is RetryableError || this is PermanentError

    /**
     * Verifica si se debe reintentar
     */
    fun shouldRetry(): Boolean = this is RetryableError

    /**
     * Verifica si es un conflicto
     */
    fun isConflict(): Boolean = this is Conflict

    /**
     * Obtiene los datos si es exitoso, null en caso contrario
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    /**
     * Ejecuta una acción si es exitoso
     */
    inline fun onSuccess(action: (T) -> Unit): SyncResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Ejecuta una acción si hay error
     */
    inline fun onError(action: (String) -> Unit): SyncResult<T> {
        when (this) {
            is RetryableError -> action(message)
            is PermanentError -> action(message)
            else -> {}
        }
        return this
    }

    /**
     * Ejecuta una acción si hay conflicto
     */
    inline fun onConflict(action: (Conflict) -> Unit): SyncResult<T> {
        if (this is Conflict) action(this)
        return this
    }

    companion object {
        /**
         * Crea un resultado exitoso
         */
        fun <T> success(data: T): SyncResult<T> = Success(data)

        /**
         * Crea un error reintentar
         */
        fun retry(message: String, exception: Throwable? = null): SyncResult<Nothing> =
            RetryableError(message, exception)

        /**
         * Crea un error permanente
         */
        fun fail(
            message: String,
            errorCode: String,
            exception: Throwable? = null
        ): SyncResult<Nothing> = PermanentError(message, errorCode, exception)

        /**
         * Crea un resultado de conflicto
         */
        fun conflict(
            message: String,
            type: ConflictType,
            details: Map<String, Any>? = null
        ): SyncResult<Nothing> = Conflict(message, type, details)
    }
}
