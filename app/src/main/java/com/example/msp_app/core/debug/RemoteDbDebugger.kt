package com.example.msp_app.core.debug

import android.content.Context
import android.util.Log
import com.example.msp_app.core.debug.models.CommandStatus
import com.example.msp_app.core.debug.models.CommandType
import com.example.msp_app.core.debug.models.DebugCommand
import com.example.msp_app.core.debug.models.DebugConfig
import com.example.msp_app.core.debug.models.DebugResult
import com.example.msp_app.core.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sistema de debug remoto de base de datos
 *
 * Permite ejecutar queries SQL y exportar la base de datos de forma remota
 * controlado desde Firebase Firestore.
 *
 * Uso:
 * 1. En Firebase Console, crear documento config/db_debug con enabled: true
 * 2. Agregar email del usuario a allowedDevices
 * 3. Crear documento en db_debug_commands con el query deseado
 * 4. Ver resultado en db_debug_results
 */
class RemoteDbDebugger private constructor(private val context: Context) {

    companion object {
        private const val TAG = "RemoteDbDebugger"

        @Volatile
        private var INSTANCE: RemoteDbDebugger? = null

        fun getInstance(context: Context): RemoteDbDebugger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteDbDebugger(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Inicializa el debugger. Llamar desde Application.onCreate()
         */
        fun init(context: Context) {
            getInstance(context).startListening()
        }
    }

    private val firestore: FirebaseFirestore = Firebase.firestore
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val commandExecutor = DebugCommandExecutor(context)
    private val exportManager = DbExportManager(context)

    private var configListener: ListenerRegistration? = null
    private var commandsListener: ListenerRegistration? = null
    private var currentConfig: DebugConfig = DebugConfig()

    private var isListening = false

    /**
     * Inicia los listeners de Firestore
     */
    fun startListening() {
        if (isListening) {
            Log.d(TAG, "Ya está escuchando")
            return
        }

        Log.d(TAG, "Iniciando listeners de debug remoto")
        isListening = true

        startConfigListener()
    }

    /**
     * Detiene todos los listeners
     */
    fun stopListening() {
        Log.d(TAG, "Deteniendo listeners de debug remoto")
        configListener?.remove()
        commandsListener?.remove()
        configListener = null
        commandsListener = null
        isListening = false
    }

    /**
     * Listener para cambios en la configuración
     */
    private fun startConfigListener() {
        configListener = firestore
            .collection(Constants.COLLECTION_CONFIG)
            .document(Constants.DOCUMENT_DB_DEBUG)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error en config listener", error)
                    return@addSnapshotListener
                }

                val data = snapshot?.data
                if (data != null) {
                    currentConfig = DebugConfig.fromMap(data)
                    Log.d(TAG, "Config actualizada: enabled=${currentConfig.enabled}, " +
                            "allowedDevices=${currentConfig.allowedDevices.size}")

                    // Si está habilitado y este dispositivo está permitido, escuchar comandos
                    if (shouldListenForCommands()) {
                        startCommandsListener()
                    } else {
                        commandsListener?.remove()
                        commandsListener = null
                        Log.d(TAG, "Debug deshabilitado o dispositivo no permitido")
                    }
                } else {
                    Log.d(TAG, "Documento de config no existe, debug deshabilitado")
                    currentConfig = DebugConfig()
                    commandsListener?.remove()
                    commandsListener = null
                }
            }
    }

    /**
     * Verifica si este dispositivo debe escuchar comandos
     */
    private fun shouldListenForCommands(): Boolean {
        if (!currentConfig.enabled) return false

        val currentUserEmail = auth.currentUser?.email ?: return false
        return currentConfig.allowedDevices.isEmpty() ||
               currentConfig.allowedDevices.contains(currentUserEmail)
    }

    /**
     * Listener para comandos pendientes
     */
    private fun startCommandsListener() {
        if (commandsListener != null) return

        val currentUserEmail = auth.currentUser?.email ?: return

        Log.d(TAG, "Iniciando listener de comandos para: $currentUserEmail")

        commandsListener = firestore
            .collection(Constants.COLLECTION_DB_DEBUG_COMMANDS)
            .whereEqualTo("targetUserId", currentUserEmail)
            .whereEqualTo("status", CommandStatus.PENDING.name)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error en commands listener", error)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val command = DebugCommand.fromMap(change.document.id, change.document.data)
                        Log.d(TAG, "Nuevo comando recibido: ${command.id} - ${command.commandType}")
                        processCommand(command)
                    }
                }
            }
    }

    /**
     * Procesa un comando recibido
     */
    private fun processCommand(command: DebugCommand) {
        scope.launch {
            try {
                // Marcar como procesando
                updateCommandStatus(command.id, CommandStatus.PROCESSING)

                val result = when (command.commandType) {
                    CommandType.EXPORT -> {
                        if (currentConfig.exportEnabled) {
                            exportManager.exportAndUpload(command.id)
                        } else {
                            DebugResult(
                                commandId = command.id,
                                status = CommandStatus.ERROR,
                                errorMessage = "Exportación deshabilitada en la configuración",
                                executionTimeMs = 0
                            )
                        }
                    }
                    else -> commandExecutor.execute(command, currentConfig)
                }

                // Guardar resultado
                saveResult(result)

                // Actualizar estado del comando
                updateCommandStatus(command.id, result.status)

                Log.d(TAG, "Comando ${command.id} procesado: ${result.status}")

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando comando: ${command.id}", e)
                updateCommandStatus(command.id, CommandStatus.ERROR)

                saveResult(DebugResult(
                    commandId = command.id,
                    status = CommandStatus.ERROR,
                    errorMessage = "${e.javaClass.simpleName}: ${e.message}",
                    executionTimeMs = 0
                ))
            }
        }
    }

    /**
     * Actualiza el estado de un comando en Firestore
     */
    private fun updateCommandStatus(commandId: String, status: CommandStatus) {
        firestore
            .collection(Constants.COLLECTION_DB_DEBUG_COMMANDS)
            .document(commandId)
            .update("status", status.name)
            .addOnFailureListener { e ->
                Log.e(TAG, "Error actualizando estado del comando: $commandId", e)
            }
    }

    /**
     * Guarda el resultado de un comando en Firestore
     */
    private fun saveResult(result: DebugResult) {
        firestore
            .collection(Constants.COLLECTION_DB_DEBUG_RESULTS)
            .add(result.toMap())
            .addOnSuccessListener { docRef ->
                Log.d(TAG, "Resultado guardado: ${docRef.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error guardando resultado", e)
            }
    }
}
