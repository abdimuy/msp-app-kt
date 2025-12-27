package com.example.msp_app.core.sync

/**
 * Estados posibles de una entidad sincronizable
 */
enum class SyncStatus {
    /** Creado localmente, pendiente de enviar */
    PENDING_CREATE,

    /** Editado localmente, pendiente de enviar cambios */
    PENDING_UPDATE,

    /** Marcado para eliminar, pendiente de sincronizar */
    PENDING_DELETE,

    /** Sincronizado correctamente con el servidor */
    SYNCED,

    /** Error en la sincronización, requiere intervención */
    ERROR,

    /** En proceso de sincronización */
    SYNCING;

    fun isPending(): Boolean = this in listOf(PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE)

    fun needsSync(): Boolean = isPending() || this == ERROR

    companion object {
        fun fromString(value: String?): SyncStatus {
            return entries.find { it.name == value } ?: PENDING_CREATE
        }
    }
}
