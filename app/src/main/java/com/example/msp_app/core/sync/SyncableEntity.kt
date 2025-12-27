package com.example.msp_app.core.sync

/**
 * Interface que deben implementar las entidades que se sincronizan offline.
 * Proporciona los campos mínimos necesarios para el tracking de sincronización.
 */
interface SyncableEntity {
    /** ID único de la entidad */
    val syncId: String

    /** Estado actual de sincronización */
    val syncStatus: SyncStatus

    /** Timestamp de última modificación local (millis) */
    val lastModified: Long

    /** Número de intentos de sincronización fallidos */
    val syncAttempts: Int

    /** Último error de sincronización (si aplica) */
    val lastSyncError: String?

    /**
     * Verifica si la entidad necesita sincronización
     */
    fun needsSync(): Boolean = syncStatus.needsSync()

    /**
     * Verifica si la entidad está pendiente de creación
     */
    fun isPendingCreate(): Boolean = syncStatus == SyncStatus.PENDING_CREATE

    /**
     * Verifica si la entidad está pendiente de actualización
     */
    fun isPendingUpdate(): Boolean = syncStatus == SyncStatus.PENDING_UPDATE

    /**
     * Verifica si la entidad tiene error de sincronización
     */
    fun hasError(): Boolean = syncStatus == SyncStatus.ERROR
}

/**
 * Interface para repositorios de entidades sincronizables.
 * Define las operaciones básicas necesarias para el sistema de sincronización.
 *
 * @param T Tipo de entidad que maneja el repositorio
 */
interface SyncableRepository<T : SyncableEntity> {

    /**
     * Obtener entidad por ID
     * @param id ID de la entidad
     * @return La entidad o null si no existe
     */
    suspend fun getById(id: String): T?

    /**
     * Obtener todas las entidades pendientes de sincronización
     * @return Lista de entidades que necesitan sincronización
     */
    suspend fun getPendingSync(): List<T>

    /**
     * Actualizar estado de sincronización
     * @param id ID de la entidad
     * @param status Nuevo estado
     * @param error Mensaje de error opcional
     */
    suspend fun updateSyncStatus(id: String, status: SyncStatus, error: String? = null)

    /**
     * Incrementar contador de intentos de sincronización
     * @param id ID de la entidad
     */
    suspend fun incrementSyncAttempts(id: String)

    /**
     * Marcar entidad como sincronizada exitosamente
     * @param id ID de la entidad
     */
    suspend fun markAsSynced(id: String)

    /**
     * Eliminar entidad (usado después de sync delete exitoso)
     * @param id ID de la entidad
     */
    suspend fun delete(id: String)

    /**
     * Obtener número de entidades pendientes
     * @return Conteo de entidades pendientes
     */
    suspend fun getPendingCount(): Int = getPendingSync().size

    /**
     * Resetear intentos de sincronización fallidos
     * @param id ID de la entidad
     */
    suspend fun resetSyncAttempts(id: String)
}

/**
 * Data class para trackear metadatos de sincronización.
 * Útil para entidades que no pueden implementar SyncableEntity directamente.
 */
data class SyncMetadata(
    val entityId: String,
    val entityType: String,
    val status: SyncStatus = SyncStatus.PENDING_CREATE,
    val lastModified: Long = System.currentTimeMillis(),
    val syncAttempts: Int = 0,
    val lastSyncError: String? = null,
    val lastSyncTime: Long? = null
) {
    fun needsSync(): Boolean = status.needsSync()

    fun withStatus(newStatus: SyncStatus, error: String? = null): SyncMetadata = copy(
        status = newStatus,
        lastSyncError = error,
        lastModified = System.currentTimeMillis()
    )

    fun withIncrementedAttempts(): SyncMetadata = copy(
        syncAttempts = syncAttempts + 1
    )

    fun asSynced(): SyncMetadata = copy(
        status = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncTime = System.currentTimeMillis()
    )
}
