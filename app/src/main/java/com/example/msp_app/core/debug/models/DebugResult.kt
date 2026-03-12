package com.example.msp_app.core.debug.models

import com.google.firebase.Timestamp

/**
 * Resultado de la ejecución de un comando de debug
 * Se escribe en Firestore: db_debug_results/{id}
 */
data class DebugResult(
    val commandId: String,
    val status: CommandStatus,
    val columnNames: List<String>? = null,
    val data: List<Map<String, Any?>>? = null,
    val rowCount: Int? = null,
    val errorMessage: String? = null,
    val exportUrl: String? = null,
    val executionTimeMs: Long,
    val executedAt: Timestamp = Timestamp.now(),
    val deviceInfo: Map<String, String> = emptyMap()
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "commandId" to commandId,
            "status" to status.name,
            "columnNames" to columnNames,
            "data" to data,
            "rowCount" to rowCount,
            "errorMessage" to errorMessage,
            "exportUrl" to exportUrl,
            "executionTimeMs" to executionTimeMs,
            "executedAt" to executedAt,
            "deviceInfo" to deviceInfo
        ).filterValues { it != null }
    }
}
