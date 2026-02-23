package com.example.msp_app.core.debug

import android.content.Context
import android.util.Log
import com.example.msp_app.core.debug.models.CommandStatus
import com.example.msp_app.core.debug.models.DebugResult
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestiona la exportación de la base de datos y subida a Firebase Storage
 */
class DbExportManager(private val context: Context) {

    companion object {
        private const val TAG = "DbExportManager"
        private const val DB_NAME = "msp_db"
        private const val STORAGE_PATH = "db_exports"
    }

    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Exporta la base de datos y la sube a Firebase Storage
     * @param commandId ID del comando que solicitó la exportación
     * @return DebugResult con la URL de descarga o error
     */
    suspend fun exportAndUpload(commandId: String): DebugResult {
        val startTime = System.currentTimeMillis()

        return try {
            // Obtener ruta del archivo de base de datos
            val dbFile = context.getDatabasePath(DB_NAME)

            if (!dbFile.exists()) {
                return createErrorResult(
                    commandId,
                    "Base de datos no encontrada: $DB_NAME",
                    startTime
                )
            }

            // Crear copia temporal para evitar problemas de bloqueo
            val tempFile = createTempCopy(dbFile)

            if (tempFile == null) {
                return createErrorResult(
                    commandId,
                    "No se pudo crear copia temporal de la base de datos",
                    startTime
                )
            }

            try {
                // Subir a Firebase Storage
                val downloadUrl = uploadToStorage(tempFile)

                val executionTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Export completado en ${executionTime}ms. URL: $downloadUrl")

                DebugResult(
                    commandId = commandId,
                    status = CommandStatus.SUCCESS,
                    exportUrl = downloadUrl,
                    executionTimeMs = executionTime,
                    executedAt = Timestamp.now(),
                    data = listOf(
                        mapOf(
                            "fileName" to tempFile.name,
                            "fileSize" to dbFile.length(),
                            "downloadUrl" to downloadUrl
                        )
                    )
                )
            } finally {
                // Limpiar archivo temporal
                tempFile.delete()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error en exportAndUpload", e)
            createErrorResult(commandId, "${e.javaClass.simpleName}: ${e.message}", startTime)
        }
    }

    /**
     * Crea una copia temporal de la base de datos
     */
    private fun createTempCopy(dbFile: File): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val userEmail = auth.currentUser?.email?.replace("@", "_")?.replace(".", "_") ?: "unknown"
            val tempFileName = "msp_db_${userEmail}_$timestamp.db"

            val tempFile = File(context.cacheDir, tempFileName)
            dbFile.copyTo(tempFile, overwrite = true)

            Log.d(TAG, "Copia temporal creada: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error creando copia temporal", e)
            null
        }
    }

    /**
     * Sube el archivo a Firebase Storage y retorna la URL de descarga
     */
    private suspend fun uploadToStorage(file: File): String {
        val userEmail = auth.currentUser?.email?.replace("@", "_")?.replace(".", "_") ?: "unknown"
        val storagePath = "$STORAGE_PATH/$userEmail/${file.name}"

        val storageRef = storage.reference.child(storagePath)

        Log.d(TAG, "Subiendo archivo a: $storagePath")

        // Subir archivo
        storageRef.putFile(android.net.Uri.fromFile(file)).await()

        // Obtener URL de descarga
        val downloadUrl = storageRef.downloadUrl.await()

        return downloadUrl.toString()
    }

    /**
     * Crea un resultado de error
     */
    private fun createErrorResult(
        commandId: String,
        errorMessage: String,
        startTime: Long
    ): DebugResult {
        return DebugResult(
            commandId = commandId,
            status = CommandStatus.ERROR,
            errorMessage = errorMessage,
            executionTimeMs = System.currentTimeMillis() - startTime,
            executedAt = Timestamp.now()
        )
    }
}
