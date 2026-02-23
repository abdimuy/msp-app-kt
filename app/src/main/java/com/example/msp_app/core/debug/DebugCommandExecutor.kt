package com.example.msp_app.core.debug

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.msp_app.BuildConfig
import com.example.msp_app.core.debug.models.CommandStatus
import com.example.msp_app.core.debug.models.CommandType
import com.example.msp_app.core.debug.models.DebugCommand
import com.example.msp_app.core.debug.models.DebugConfig
import com.example.msp_app.core.debug.models.DebugResult
import com.example.msp_app.data.local.AppDatabase
import com.google.firebase.Timestamp

/**
 * Ejecuta queries SQL de forma segura con validaciones
 */
class DebugCommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "DebugCommandExecutor"

        // Queries peligrosas que pueden modificar datos
        private val DANGEROUS_KEYWORDS = listOf(
            "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "TRUNCATE",
            "CREATE", "REPLACE", "ATTACH", "DETACH"
        )
    }

    private val database: AppDatabase by lazy { AppDatabase.getInstance(context) }

    /**
     * Ejecuta un comando y retorna el resultado
     */
    fun execute(command: DebugCommand, config: DebugConfig): DebugResult {
        val startTime = System.currentTimeMillis()

        return try {
            val result = when (command.commandType) {
                CommandType.QUERY -> executeQuery(command.query ?: "", config)
                CommandType.SCHEMA -> executeSchema()
                CommandType.TABLE_INFO -> executeTableInfo(command.tableName ?: "")
                CommandType.EXPORT -> {
                    // Export se maneja por separado con DbExportManager
                    QueryResult(
                        success = false,
                        errorMessage = "Use EXPORT command type via DbExportManager"
                    )
                }
            }

            val executionTime = System.currentTimeMillis() - startTime

            DebugResult(
                commandId = command.id,
                status = if (result.success) CommandStatus.SUCCESS else CommandStatus.ERROR,
                columnNames = result.columnNames,
                data = result.data,
                rowCount = result.data?.size,
                errorMessage = result.errorMessage,
                executionTimeMs = executionTime,
                executedAt = Timestamp.now(),
                deviceInfo = getDeviceInfo()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${command.id}", e)
            val executionTime = System.currentTimeMillis() - startTime

            DebugResult(
                commandId = command.id,
                status = CommandStatus.ERROR,
                errorMessage = "${e.javaClass.simpleName}: ${e.message}",
                executionTimeMs = executionTime,
                executedAt = Timestamp.now(),
                deviceInfo = getDeviceInfo()
            )
        }
    }

    /**
     * Ejecuta un query SQL con validaciones
     */
    private fun executeQuery(query: String, config: DebugConfig): QueryResult {
        if (query.isBlank()) {
            return QueryResult(success = false, errorMessage = "Query vacío")
        }

        // Validar queries peligrosos
        if (config.blockDangerousQueries && isDangerousQuery(query)) {
            return QueryResult(
                success = false,
                errorMessage = "Query bloqueado: contiene operaciones peligrosas (DROP, DELETE, UPDATE, etc.)"
            )
        }

        val db = database.openHelper.readableDatabase
        return executeRawQuery(db, query, config.maxQueryRows)
    }

    /**
     * Obtiene el schema de todas las tablas
     */
    private fun executeSchema(): QueryResult {
        val db = database.openHelper.readableDatabase
        val schemaQuery = """
            SELECT name, sql FROM sqlite_master
            WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%'
            ORDER BY name
        """.trimIndent()

        return executeRawQuery(db, schemaQuery, Int.MAX_VALUE)
    }

    /**
     * Obtiene información de una tabla específica
     */
    private fun executeTableInfo(tableName: String): QueryResult {
        if (tableName.isBlank()) {
            return QueryResult(success = false, errorMessage = "Nombre de tabla vacío")
        }

        val db = database.openHelper.readableDatabase
        val pragmaQuery = "PRAGMA table_info($tableName)"

        return executeRawQuery(db, pragmaQuery, Int.MAX_VALUE)
    }

    /**
     * Ejecuta un query raw y convierte el resultado
     */
    private fun executeRawQuery(db: SupportSQLiteDatabase, query: String, maxRows: Int): QueryResult {
        var cursor: Cursor? = null
        return try {
            cursor = db.query(query)
            val columnNames = cursor.columnNames.toList()
            val data = mutableListOf<Map<String, Any?>>()

            var rowCount = 0
            while (cursor.moveToNext() && rowCount < maxRows) {
                val row = mutableMapOf<String, Any?>()
                for (i in 0 until cursor.columnCount) {
                    val columnName = cursor.getColumnName(i)
                    val value = when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                        Cursor.FIELD_TYPE_BLOB -> "[BLOB ${cursor.getBlob(i).size} bytes]"
                        else -> cursor.getString(i)
                    }
                    row[columnName] = value
                }
                data.add(row)
                rowCount++
            }

            val totalRows = if (cursor.moveToLast()) cursor.position + 1 else 0
            val truncated = totalRows > maxRows

            QueryResult(
                success = true,
                columnNames = columnNames,
                data = data,
                truncated = truncated,
                totalRows = totalRows
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing query: $query", e)
            QueryResult(success = false, errorMessage = "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            cursor?.close()
        }
    }

    /**
     * Verifica si el query contiene operaciones peligrosas
     */
    private fun isDangerousQuery(query: String): Boolean {
        val upperQuery = query.uppercase().trim()
        return DANGEROUS_KEYWORDS.any { keyword ->
            upperQuery.startsWith(keyword) || upperQuery.contains(" $keyword ")
        }
    }

    /**
     * Información del dispositivo para incluir en el resultado
     */
    private fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "androidVersion" to Build.VERSION.RELEASE,
            "appVersion" to BuildConfig.VERSION_NAME
        )
    }

    /**
     * Resultado interno del query
     */
    private data class QueryResult(
        val success: Boolean,
        val columnNames: List<String>? = null,
        val data: List<Map<String, Any?>>? = null,
        val errorMessage: String? = null,
        val truncated: Boolean = false,
        val totalRows: Int = 0
    )
}
