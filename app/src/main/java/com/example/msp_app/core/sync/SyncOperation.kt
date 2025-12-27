package com.example.msp_app.core.sync

/**
 * Tipo de operación de sincronización
 */
sealed class SyncOperation {
    abstract val entityId: String
    abstract val entityType: String
    abstract val priority: Int

    data class Create(
        override val entityId: String,
        override val entityType: String,
        override val priority: Int = 1
    ) : SyncOperation()

    data class Update(
        override val entityId: String,
        override val entityType: String,
        override val priority: Int = 2
    ) : SyncOperation()

    data class Delete(
        override val entityId: String,
        override val entityType: String,
        override val priority: Int = 3
    ) : SyncOperation()

    fun toTypeString(): String = when (this) {
        is Create -> "CREATE"
        is Update -> "UPDATE"
        is Delete -> "DELETE"
    }

    companion object {
        fun fromString(type: String, entityId: String, entityType: String): SyncOperation {
            return when (type.uppercase()) {
                "CREATE" -> Create(entityId, entityType)
                "UPDATE" -> Update(entityId, entityType)
                "DELETE" -> Delete(entityId, entityType)
                else -> Create(entityId, entityType)
            }
        }
    }
}
