package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.msp_app.core.debug.DbExportManager
import com.example.msp_app.core.debug.models.CommandStatus
import com.example.msp_app.core.utils.Constants
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Worker para exportar la base de datos en background usando WorkManager
 *
 * Se puede usar como alternativa a la exportación directa cuando se necesita
 * garantizar la ejecución incluso si la app se cierra.
 */
class DbExportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DbExportWorker"
        private const val KEY_COMMAND_ID = "command_id"

        /**
         * Encola un trabajo de exportación
         */
        fun enqueue(context: Context, commandId: String) {
            val inputData = Data.Builder()
                .putString(KEY_COMMAND_ID, commandId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DbExportWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Export work enqueued for command: $commandId")
        }
    }

    private val exportManager = DbExportManager(applicationContext)
    private val firestore = Firebase.firestore

    override suspend fun doWork(): Result {
        val commandId = inputData.getString(KEY_COMMAND_ID)
            ?: return Result.failure().also {
                Log.e(TAG, "No se proporcionó command_id")
            }

        Log.d(TAG, "Iniciando exportación para comando: $commandId")

        return try {
            // Actualizar estado a procesando
            updateCommandStatus(commandId, CommandStatus.PROCESSING)

            // Ejecutar exportación
            val result = exportManager.exportAndUpload(commandId)

            // Guardar resultado
            saveResult(result.toMap())

            // Actualizar estado del comando
            updateCommandStatus(commandId, result.status)

            if (result.status == CommandStatus.SUCCESS) {
                Log.d(TAG, "Exportación completada exitosamente: ${result.exportUrl}")
                Result.success()
            } else {
                Log.e(TAG, "Exportación falló: ${result.errorMessage}")
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error durante la exportación", e)

            // Guardar error
            saveResult(mapOf(
                "commandId" to commandId,
                "status" to CommandStatus.ERROR.name,
                "errorMessage" to "${e.javaClass.simpleName}: ${e.message}",
                "executionTimeMs" to 0
            ))

            updateCommandStatus(commandId, CommandStatus.ERROR)
            Result.failure()
        }
    }

    private suspend fun updateCommandStatus(commandId: String, status: CommandStatus) {
        try {
            firestore
                .collection(Constants.COLLECTION_DB_DEBUG_COMMANDS)
                .document(commandId)
                .update("status", status.name)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando estado del comando: $commandId", e)
        }
    }

    private suspend fun saveResult(resultMap: Map<String, Any?>) {
        try {
            firestore
                .collection(Constants.COLLECTION_DB_DEBUG_RESULTS)
                .add(resultMap)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando resultado", e)
        }
    }
}
