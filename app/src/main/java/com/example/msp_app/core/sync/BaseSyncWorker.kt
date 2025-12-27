package com.example.msp_app.core.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.logging.RemoteLogger

/**
 * Worker base genérico para sincronización offline.
 * Las implementaciones específicas solo necesitan proveer el SyncHandler.
 *
 * @param T Tipo de entidad a sincronizar
 * @param R Tipo de respuesta del servidor
 */
abstract class BaseSyncWorker<T, R>(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    protected val logger: RemoteLogger by lazy {
        RemoteLogger.getInstance(applicationContext)
    }

    /**
     * Handler específico para el tipo de entidad.
     * Las subclases deben proveer la implementación.
     */
    abstract fun createHandler(): SyncHandler<T, R>

    private val handler: SyncHandler<T, R> by lazy { createHandler() }

    /** Key del input data para el ID de la entidad */
    protected open val entityIdKey: String = "entity_id"

    /** Key del input data para el tipo de operación */
    protected open val operationTypeKey: String = "operation_type"

    override suspend fun doWork(): Result {
        val entityId = inputData.getString(entityIdKey)
        if (entityId.isNullOrBlank()) {
            log("No entity ID provided", isError = true)
            logRemote("SYNC_MISSING_ID", null, "unknown")
            return Result.failure()
        }

        val operationType = inputData.getString(operationTypeKey) ?: "CREATE"
        val operation = SyncOperation.fromString(operationType, entityId, handler.entityType)

        // Extraer todos los datos adicionales del inputData
        val additionalData = extractAdditionalData()
        val syncContext = SyncContext(
            entityId = entityId,
            operationType = operationType,
            additionalData = additionalData
        )

        val entity = handler.getEntity(applicationContext, entityId)
        if (entity == null) {
            log("Entity not found: $entityId", isError = true)
            logRemote("SYNC_ENTITY_NOT_FOUND", null, entityId)
            return Result.failure()
        }

        // Verificar precondiciones
        val canProceed = handler.onBeforeSync(applicationContext, entity, operation)
        if (!canProceed) {
            log("Sync cancelled by onBeforeSync for: $entityId")
            return Result.failure()
        }

        return try {
            log("Starting sync for ${handler.entityType}: $entityId (${operation.toTypeString()})")

            // Preparar request con contexto de sincronización
            val request = handler.prepareRequest(applicationContext, entity, operation, syncContext)

            // Ejecutar sincronización
            val result = handler.executeSync(applicationContext, entity, operation, request)

            handleResult(entity, result, entityId)

        } catch (e: Exception) {
            log("Unexpected error syncing $entityId: ${e.message}", isError = true)
            logRemote("SYNC_UNEXPECTED_ERROR", e, entityId)
            Result.retry()
        }
    }

    /**
     * Extrae datos adicionales del inputData excluyendo las keys del sistema.
     */
    private fun extractAdditionalData(): Map<String, String> {
        val systemKeys = setOf(entityIdKey, operationTypeKey)
        val result = mutableMapOf<String, String>()

        inputData.keyValueMap.forEach { (key, value) ->
            if (key !in systemKeys && value is String) {
                result[key] = value
            }
        }

        return result
    }

    private suspend fun handleResult(entity: T, result: SyncResult<R>, entityId: String): Result {
        return when (result) {
            is SyncResult.Success -> {
                handler.onSyncSuccess(applicationContext, entity, result.data)
                log("Sync successful for $entityId")
                logRemote("SYNC_SUCCESS", null, entityId)
                Result.success()
            }

            is SyncResult.RetryableError -> {
                log("Retryable error for $entityId: ${result.message}", isError = true)
                logRemote("SYNC_RETRY", result.exception, entityId, mapOf(
                    "httpCode" to (result.httpCode?.toString() ?: "N/A"),
                    "attemptCount" to runAttemptCount.toString()
                ))

                // Si excedimos el máximo de reintentos, fallar permanentemente
                if (runAttemptCount >= handler.config.maxRetries) {
                    log("Max retries exceeded for $entityId", isError = true)
                    handler.onSyncError(
                        applicationContext,
                        entity,
                        SyncResult.PermanentError(
                            message = "Máximo de reintentos excedido: ${result.message}",
                            errorCode = "MAX_RETRIES_EXCEEDED",
                            exception = result.exception
                        )
                    )
                    return Result.failure()
                }

                Result.retry()
            }

            is SyncResult.PermanentError -> {
                handler.onSyncError(applicationContext, entity, result)
                log("Permanent error for $entityId: ${result.message}", isError = true)
                logRemote(
                    "SYNC_PERMANENT_ERROR",
                    result.exception,
                    entityId,
                    mapOf("errorCode" to result.errorCode)
                )
                Result.failure()
            }

            is SyncResult.Conflict -> {
                handler.onConflict(applicationContext, entity, result)
                log("Conflict for $entityId: ${result.message} (${result.conflictType})")
                logRemote(
                    "SYNC_CONFLICT",
                    null,
                    entityId,
                    mapOf("conflictType" to result.conflictType.name)
                )

                // Para duplicados, consideramos éxito (ya existe en servidor)
                when (result.conflictType) {
                    ConflictType.DUPLICATE -> Result.success()
                    else -> Result.failure()
                }
            }

            is SyncResult.Cancelled -> {
                log("Sync cancelled for $entityId")
                Result.failure()
            }
        }
    }

    private fun log(message: String, isError: Boolean = false) {
        if (handler.config.enableLogging) {
            val tag = handler.config.logTag
            val formattedMessage = "[${handler.entityType}] $message"
            if (isError) {
                Log.e(tag, formattedMessage)
            } else {
                Log.d(tag, formattedMessage)
            }
        }
    }

    private fun logRemote(
        action: String,
        error: Throwable?,
        entityId: String,
        extraData: Map<String, Any> = emptyMap()
    ) {
        val data = mapOf(
            "entityId" to entityId,
            "entityType" to handler.entityType,
            "attempt" to runAttemptCount
        ) + extraData

        if (error != null) {
            logger.error(
                module = "OFFLINE_SYNC",
                action = action,
                message = "${handler.entityType} sync: ${error.message}",
                error = error,
                data = data
            )
        } else {
            logger.info(
                module = "OFFLINE_SYNC",
                action = action,
                message = "${handler.entityType} sync completed",
                data = data
            )
        }
    }
}
