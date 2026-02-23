package com.example.msp_app.core.debug.models

/**
 * Configuración del sistema de debug remoto
 * Se sincroniza desde Firestore: config/db_debug
 */
data class DebugConfig(
    val enabled: Boolean = false,
    val allowedDevices: List<String> = emptyList(),
    val maxQueryRows: Int = 1000,
    val blockDangerousQueries: Boolean = true,
    val exportEnabled: Boolean = true
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): DebugConfig {
            return DebugConfig(
                enabled = map["enabled"] as? Boolean ?: false,
                allowedDevices = (map["allowedDevices"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                maxQueryRows = (map["maxQueryRows"] as? Long)?.toInt() ?: 1000,
                blockDangerousQueries = map["blockDangerousQueries"] as? Boolean ?: true,
                exportEnabled = map["exportEnabled"] as? Boolean ?: true
            )
        }
    }
}
