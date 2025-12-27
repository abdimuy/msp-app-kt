package com.example.msp_app.core.sync

import android.content.Context

/**
 * Contexto de sincronización que contiene datos adicionales del worker.
 */
data class SyncContext(
    val entityId: String,
    val operationType: String,
    val additionalData: Map<String, String> = emptyMap()
) {
    fun getString(key: String): String? = additionalData[key]
    fun getStringOrDefault(key: String, default: String): String = additionalData[key] ?: default
}

/**
 * Interface que define cómo sincronizar una entidad específica.
 * Cada tipo de entidad implementa su propio handler con la lógica específica.
 *
 * @param T Tipo de entidad a sincronizar
 * @param R Tipo de respuesta del servidor
 */
interface SyncHandler<T, R> {

    /**
     * Tipo de entidad que maneja este handler
     */
    val entityType: String

    /**
     * Configuración de sincronización
     */
    val config: SyncConfig

    /**
     * Obtener la entidad desde la base de datos local
     *
     * @param context Context de Android
     * @param entityId ID de la entidad
     * @return La entidad o null si no existe
     */
    suspend fun getEntity(context: Context, entityId: String): T?

    /**
     * Preparar los datos para enviar al servidor.
     * Aquí se transforma la entidad local al DTO/Request esperado por la API.
     *
     * @param context Context de Android
     * @param entity Entidad a sincronizar
     * @param operation Tipo de operación (CREATE, UPDATE, DELETE)
     * @param syncContext Contexto con datos adicionales del worker
     * @return Objeto preparado para enviar (puede ser DTO, MultipartRequest, etc.)
     */
    suspend fun prepareRequest(
        context: Context,
        entity: T,
        operation: SyncOperation,
        syncContext: SyncContext
    ): Any

    /**
     * Ejecutar la operación de sincronización contra la API.
     *
     * @param context Context de Android
     * @param entity Entidad a sincronizar
     * @param operation Tipo de operación
     * @param request Request preparado por prepareRequest()
     * @return Resultado de la sincronización
     */
    suspend fun executeSync(
        context: Context,
        entity: T,
        operation: SyncOperation,
        request: Any
    ): SyncResult<R>

    /**
     * Callback ejecutado después de sincronización exitosa.
     * Usado para actualizar estado local, limpiar archivos temporales, etc.
     *
     * @param context Context de Android
     * @param entity Entidad sincronizada
     * @param response Respuesta del servidor
     */
    suspend fun onSyncSuccess(context: Context, entity: T, response: R)

    /**
     * Callback ejecutado después de un error permanente.
     * Usado para marcar la entidad con error, notificar al usuario, etc.
     *
     * @param context Context de Android
     * @param entity Entidad que falló
     * @param error Detalle del error
     */
    suspend fun onSyncError(context: Context, entity: T, error: SyncResult.PermanentError)

    /**
     * Callback para manejar conflictos.
     * Usado para resolver conflictos como duplicados, stock insuficiente, etc.
     *
     * @param context Context de Android
     * @param entity Entidad en conflicto
     * @param conflict Detalle del conflicto
     */
    suspend fun onConflict(context: Context, entity: T, conflict: SyncResult.Conflict)

    /**
     * Callback opcional antes de iniciar sincronización.
     * Útil para preparar datos adicionales, validar precondiciones, etc.
     *
     * @param context Context de Android
     * @param entity Entidad a sincronizar
     * @param operation Tipo de operación
     * @return true si se puede continuar, false para cancelar
     */
    suspend fun onBeforeSync(context: Context, entity: T, operation: SyncOperation): Boolean = true

    /**
     * Obtiene datos adicionales a pasar al worker.
     * Estos datos se incluirán en el inputData del WorkManager.
     *
     * @param entity Entidad a sincronizar
     * @return Map con datos adicionales
     */
    fun getAdditionalWorkerData(entity: T): Map<String, String> = emptyMap()
}

/**
 * Implementación base abstracta de SyncHandler con comportamiento por defecto.
 * Extiende esta clase para implementaciones más simples.
 */
abstract class BaseSyncHandler<T, R> : SyncHandler<T, R> {

    override val config: SyncConfig by lazy {
        SyncConfig.simple(entityType)
    }

    override suspend fun onSyncError(context: Context, entity: T, error: SyncResult.PermanentError) {
        // Por defecto no hace nada - las implementaciones pueden sobrescribir
    }

    override suspend fun onConflict(context: Context, entity: T, conflict: SyncResult.Conflict) {
        // Por defecto no hace nada - las implementaciones pueden sobrescribir
    }

    override suspend fun onBeforeSync(context: Context, entity: T, operation: SyncOperation): Boolean {
        return true
    }

    override fun getAdditionalWorkerData(entity: T): Map<String, String> {
        return emptyMap()
    }
}
