package com.example.msp_app.core.sync

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Manager central para encolar y gestionar operaciones de sincronización offline.
 * Proporciona una API simple y unificada para todas las operaciones de sync.
 */
object OfflineSyncManager {

    /**
     * Encola una operación de sincronización.
     *
     * @param W Tipo del Worker (debe extender BaseSyncWorker)
     * @param context Context de Android
     * @param config Configuración de sincronización
     * @param entityId ID de la entidad a sincronizar
     * @param operation Tipo de operación (CREATE, UPDATE, DELETE)
     * @param additionalData Datos adicionales para el worker
     * @param replaceExisting Si true, reemplaza workers pendientes para la misma entidad
     */
    inline fun <reified W : BaseSyncWorker<*, *>> enqueue(
        context: Context,
        config: SyncConfig,
        entityId: String,
        operation: SyncOperation,
        additionalData: Map<String, String> = emptyMap(),
        replaceExisting: Boolean = false
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(config.networkType)
            .build()

        val inputData = buildInputData(entityId, operation, additionalData)

        val request = OneTimeWorkRequestBuilder<W>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(config.entityType)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                config.initialRetryDelay.inWholeMilliseconds,
                TimeUnit.MILLISECONDS
            )
            .build()

        val policy = if (replaceExisting) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }

        val uniqueName = "${config.workerNamePrefix}_${entityId}"

        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName, policy, request)
    }

    /**
     * Construye el Data para el worker con los parámetros necesarios.
     */
    fun buildInputData(
        entityId: String,
        operation: SyncOperation,
        additionalData: Map<String, String>
    ): Data {
        val builder = Data.Builder()
            .putString("entity_id", entityId)
            .putString("operation_type", operation.toTypeString())

        additionalData.forEach { (key, value) ->
            builder.putString(key, value)
        }

        return builder.build()
    }

    /**
     * Encola múltiples operaciones de sincronización del mismo tipo.
     *
     * @param W Tipo del Worker
     * @param context Context de Android
     * @param config Configuración de sincronización
     * @param entityIds Lista de IDs de entidades a sincronizar
     * @param operation Tipo de operación
     * @param additionalDataProvider Función que provee datos adicionales por entidad
     */
    inline fun <reified W : BaseSyncWorker<*, *>> enqueueBatch(
        context: Context,
        config: SyncConfig,
        entityIds: List<String>,
        operation: SyncOperation,
        crossinline additionalDataProvider: (String) -> Map<String, String> = { emptyMap() }
    ) {
        entityIds.forEach { entityId ->
            enqueue<W>(
                context = context,
                config = config,
                entityId = entityId,
                operation = SyncOperation.fromString(
                    operation.toTypeString(),
                    entityId,
                    config.entityType
                ),
                additionalData = additionalDataProvider(entityId),
                replaceExisting = true
            )
        }
    }

    /**
     * Cancela una operación pendiente.
     *
     * @param context Context de Android
     * @param config Configuración de sincronización
     * @param entityId ID de la entidad
     */
    fun cancel(context: Context, config: SyncConfig, entityId: String) {
        val uniqueName = "${config.workerNamePrefix}_${entityId}"
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
    }

    /**
     * Cancela todas las operaciones de un tipo de entidad.
     *
     * @param context Context de Android
     * @param config Configuración de sincronización
     */
    fun cancelAll(context: Context, config: SyncConfig) {
        WorkManager.getInstance(context).cancelAllWorkByTag(config.entityType)
    }

    /**
     * Obtiene el estado de una operación específica.
     *
     * @param context Context de Android
     * @param config Configuración de sincronización
     * @param entityId ID de la entidad
     * @return LiveData con información del trabajo
     */
    fun getStatus(
        context: Context,
        config: SyncConfig,
        entityId: String
    ): LiveData<List<WorkInfo>> {
        val uniqueName = "${config.workerNamePrefix}_${entityId}"
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(uniqueName)
    }

    /**
     * Verifica si hay una operación pendiente para una entidad.
     * Retorna un LiveData que se puede observar.
     *
     * @param context Context de Android
     * @param config Configuración de sincronización
     * @param entityId ID de la entidad
     * @return LiveData con información del trabajo para verificar el estado
     */
    fun observePendingWork(
        context: Context,
        config: SyncConfig,
        entityId: String
    ): LiveData<List<WorkInfo>> {
        return getStatus(context, config, entityId)
    }

    /**
     * Obtiene todos los trabajos pendientes de un tipo.
     *
     * @param context Context de Android
     * @param config Configuración de sincronización
     * @return LiveData con lista de trabajos
     */
    fun getAllPendingWork(
        context: Context,
        config: SyncConfig
    ): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(context)
            .getWorkInfosByTagLiveData(config.entityType)
    }

    /**
     * Crea un worker request personalizado (para casos avanzados).
     *
     * @param W Tipo del Worker
     * @param config Configuración de sincronización
     * @param entityId ID de la entidad
     * @param operation Tipo de operación
     * @param additionalData Datos adicionales
     * @return OneTimeWorkRequest configurado
     */
    inline fun <reified W : BaseSyncWorker<*, *>> createRequest(
        config: SyncConfig,
        entityId: String,
        operation: SyncOperation,
        additionalData: Map<String, String> = emptyMap()
    ): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(config.networkType)
            .build()

        val inputData = buildInputData(entityId, operation, additionalData)

        return OneTimeWorkRequestBuilder<W>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(config.entityType)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                config.initialRetryDelay.inWholeMilliseconds,
                TimeUnit.MILLISECONDS
            )
            .build()
    }
}

/**
 * Extension para encolar fácilmente desde un ViewModel o UseCase.
 */
inline fun <reified W : BaseSyncWorker<*, *>> Context.enqueueSync(
    config: SyncConfig,
    entityId: String,
    operation: SyncOperation,
    additionalData: Map<String, String> = emptyMap(),
    replaceExisting: Boolean = false
) {
    OfflineSyncManager.enqueue<W>(
        context = this,
        config = config,
        entityId = entityId,
        operation = operation,
        additionalData = additionalData,
        replaceExisting = replaceExisting
    )
}
