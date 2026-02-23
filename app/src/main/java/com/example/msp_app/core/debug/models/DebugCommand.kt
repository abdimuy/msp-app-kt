package com.example.msp_app.core.debug.models

import com.google.firebase.Timestamp

/**
 * Tipos de comandos soportados
 */
enum class CommandType {
    QUERY,      // Ejecutar SQL query
    EXPORT,     // Exportar DB completa
    SCHEMA,     // Obtener schema de todas las tablas
    TABLE_INFO  // Información de una tabla específica
}

/**
 * Estado del comando
 */
enum class CommandStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    ERROR
}

/**
 * Comando de debug a ejecutar
 * Se lee desde Firestore: db_debug_commands/{id}
 */
data class DebugCommand(
    val id: String = "",
    val targetUserId: String = "",
    val commandType: CommandType = CommandType.QUERY,
    val query: String? = null,
    val tableName: String? = null,
    val status: CommandStatus = CommandStatus.PENDING,
    val createdAt: Timestamp? = null
) {
    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): DebugCommand {
            val commandTypeStr = map["commandType"] as? String ?: "QUERY"
            val statusStr = map["status"] as? String ?: "PENDING"

            return DebugCommand(
                id = id,
                targetUserId = map["targetUserId"] as? String ?: "",
                commandType = try { CommandType.valueOf(commandTypeStr) } catch (e: Exception) { CommandType.QUERY },
                query = map["query"] as? String,
                tableName = map["tableName"] as? String,
                status = try { CommandStatus.valueOf(statusStr) } catch (e: Exception) { CommandStatus.PENDING },
                createdAt = map["createdAt"] as? Timestamp
            )
        }
    }
}
