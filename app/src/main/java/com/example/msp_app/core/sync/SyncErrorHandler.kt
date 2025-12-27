package com.example.msp_app.core.sync

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Handler para convertir excepciones en SyncResult apropiados.
 * Centraliza la lógica de manejo de errores HTTP y de red.
 */
object SyncErrorHandler {

    /**
     * Convierte una excepción en un SyncResult apropiado.
     *
     * @param exception Excepción capturada
     * @param config Configuración de sincronización
     * @param conflictDetector Función opcional para detectar tipo de conflicto desde el cuerpo de error
     * @return SyncResult apropiado según el tipo de error
     */
    fun handleException(
        exception: Throwable,
        config: SyncConfig,
        conflictDetector: ((String?) -> ConflictType)? = null
    ): SyncResult<Nothing> {
        return when (exception) {
            is HttpException -> handleHttpException(exception, config, conflictDetector)
            is SocketTimeoutException -> SyncResult.RetryableError(
                message = "Tiempo de espera agotado",
                exception = exception
            )
            is UnknownHostException -> SyncResult.RetryableError(
                message = "Sin conexión a internet",
                exception = exception
            )
            is IOException -> SyncResult.RetryableError(
                message = "Error de red: ${exception.message}",
                exception = exception
            )
            else -> SyncResult.RetryableError(
                message = exception.message ?: "Error desconocido",
                exception = exception
            )
        }
    }

    /**
     * Maneja específicamente excepciones HTTP.
     */
    private fun handleHttpException(
        exception: HttpException,
        config: SyncConfig,
        conflictDetector: ((String?) -> ConflictType)?
    ): SyncResult<Nothing> {
        val code = exception.code()
        val errorBody = try {
            exception.response()?.errorBody()?.string()
        } catch (e: Exception) {
            null
        }

        return when {
            // Códigos de conflicto
            code in config.conflictErrorCodes -> {
                val conflictType = conflictDetector?.invoke(errorBody)
                    ?: detectConflictType(errorBody)

                SyncResult.Conflict(
                    message = extractErrorMessage(errorBody) ?: "Conflicto detectado",
                    conflictType = conflictType,
                    details = mapOf(
                        "httpCode" to code,
                        "errorBody" to (errorBody ?: "")
                    )
                )
            }

            // Códigos no reintentables
            code in config.nonRetryableErrorCodes -> {
                SyncResult.PermanentError(
                    message = extractErrorMessage(errorBody) ?: "Error del servidor",
                    errorCode = "HTTP_$code",
                    exception = exception,
                    httpCode = code,
                    details = mapOf("errorBody" to (errorBody ?: ""))
                )
            }

            // Errores del servidor (5xx) - reintentar
            code in 500..599 -> {
                SyncResult.RetryableError(
                    message = "Error del servidor: $code",
                    exception = exception,
                    httpCode = code
                )
            }

            // Otros - reintentar por defecto
            else -> {
                SyncResult.RetryableError(
                    message = "Error HTTP: $code",
                    exception = exception,
                    httpCode = code
                )
            }
        }
    }

    /**
     * Detecta el tipo de conflicto desde el cuerpo del error.
     */
    private fun detectConflictType(errorBody: String?): ConflictType {
        if (errorBody == null) return ConflictType.OTHER

        val bodyLower = errorBody.lowercase()
        return when {
            bodyLower.contains("stock") -> ConflictType.INSUFFICIENT_STOCK
            bodyLower.contains("duplicad") || bodyLower.contains("duplicate") ||
                    bodyLower.contains("already exists") -> ConflictType.DUPLICATE
            bodyLower.contains("not found") || bodyLower.contains("no encontrad") -> ConflictType.NOT_FOUND
            bodyLower.contains("modified") || bodyLower.contains("modificad") ||
                    bodyLower.contains("concurrent") -> ConflictType.CONCURRENT_MODIFICATION
            else -> ConflictType.OTHER
        }
    }

    /**
     * Extrae el mensaje de error desde el cuerpo JSON.
     */
    private fun extractErrorMessage(errorBody: String?): String? {
        if (errorBody == null) return null

        // Intentar extraer "error" o "message" del JSON
        return try {
            val patterns = listOf(
                """"error"\s*:\s*"([^"]+)"""",
                """"message"\s*:\s*"([^"]+)"""",
                """"mensaje"\s*:\s*"([^"]+)""""
            )

            for (pattern in patterns) {
                val regex = Regex(pattern)
                regex.find(errorBody)?.groupValues?.getOrNull(1)?.let {
                    return it
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Extension para manejar llamadas a API de forma segura.
 */
suspend fun <T> safeApiCall(
    config: SyncConfig,
    conflictDetector: ((String?) -> ConflictType)? = null,
    apiCall: suspend () -> T
): SyncResult<T> {
    return try {
        val result = apiCall()
        SyncResult.Success(result)
    } catch (e: Exception) {
        SyncErrorHandler.handleException(e, config, conflictDetector)
    }
}
