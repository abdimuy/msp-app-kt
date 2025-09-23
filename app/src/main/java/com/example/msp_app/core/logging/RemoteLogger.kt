package com.example.msp_app.core.logging

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.msp_app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Sistema de logging remoto reutilizable para cualquier módulo de la aplicación
 * Guarda logs en Firebase Firestore para monitoreo remoto
 */
class RemoteLogger private constructor(private val context: Context) {

    private val firestore: FirebaseFirestore = Firebase.firestore
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "RemoteLogger"
        private const val LOGS_COLLECTION = "app_logs"
        private const val ERRORS_COLLECTION = "app_errors"
        private const val EVENTS_COLLECTION = "app_events"

        @Volatile
        private var INSTANCE: RemoteLogger? = null

        fun getInstance(context: Context): RemoteLogger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteLogger(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    enum class LogLevel {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    enum class EventType {
        USER_ACTION,
        SYSTEM_EVENT,
        NETWORK_EVENT,
        DATABASE_EVENT,
        VALIDATION_ERROR,
        BUSINESS_LOGIC,
        PERFORMANCE
    }

    /**
     * Log genérico para cualquier módulo
     */
    fun log(
        module: String,
        action: String,
        level: LogLevel = LogLevel.INFO,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        error: Throwable? = null
    ) {
        scope.launch {
            try {
                val currentUser = auth.currentUser
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                val logEntry = hashMapOf(
                    "id" to UUID.randomUUID().toString(),
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "timestampString" to timestamp,
                    "module" to module,
                    "action" to action,
                    "level" to level.name,
                    "message" to message,
                    "userId" to (currentUser?.uid ?: "anonymous"),
                    "userEmail" to (currentUser?.email ?: "anonymous"),
                    "deviceInfo" to getDeviceInfo(),
                    "appVersion" to BuildConfig.VERSION_NAME,
                    "data" to data.filterValues { it != null }
                )

                // Agregar stack trace si hay error
                error?.let {
                    logEntry["errorMessage"] = it.message ?: "Unknown error"
                    logEntry["stackTrace"] = it.stackTraceToString()
                }

                // Log local
                logLocal(module, level, "$action: $message", error)

                // Determinar colección según el nivel
                val collection = when (level) {
                    LogLevel.ERROR, LogLevel.CRITICAL -> ERRORS_COLLECTION
                    else -> LOGS_COLLECTION
                }

                // Enviar a Firebase
                firestore.collection(collection)
                    .add(logEntry)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send log to Firebase", e)
                    }

            } catch (e: Exception) {
                Log.e(TAG, "Error in logging", e)
            }
        }
    }

    /**
     * Log de evento específico
     */
    fun logEvent(
        module: String,
        eventType: EventType,
        eventName: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        scope.launch {
            try {
                val currentUser = auth.currentUser

                val eventEntry = hashMapOf(
                    "id" to UUID.randomUUID().toString(),
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "module" to module,
                    "eventType" to eventType.name,
                    "eventName" to eventName,
                    "userId" to (currentUser?.uid ?: "anonymous"),
                    "userEmail" to (currentUser?.email ?: "anonymous"),
                    "deviceInfo" to getDeviceInfo(),
                    "appVersion" to BuildConfig.VERSION_NAME,
                    "data" to data.filterValues { it != null }
                )

                firestore.collection(EVENTS_COLLECTION)
                    .add(eventEntry)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send event to Firebase", e)
                    }

            } catch (e: Exception) {
                Log.e(TAG, "Error logging event", e)
            }
        }
    }

    /**
     * Métodos de conveniencia para diferentes niveles
     */
    fun debug(module: String, action: String, message: String, data: Map<String, Any?> = emptyMap()) {
        log(module, action, LogLevel.DEBUG, message, data)
    }

    fun info(module: String, action: String, message: String, data: Map<String, Any?> = emptyMap()) {
        log(module, action, LogLevel.INFO, message, data)
    }

    fun warning(module: String, action: String, message: String, data: Map<String, Any?> = emptyMap()) {
        log(module, action, LogLevel.WARNING, message, data)
    }

    fun error(module: String, action: String, message: String, error: Throwable? = null, data: Map<String, Any?> = emptyMap()) {
        log(module, action, LogLevel.ERROR, message, data, error)
    }

    fun critical(module: String, action: String, message: String, error: Throwable? = null, data: Map<String, Any?> = emptyMap()) {
        log(module, action, LogLevel.CRITICAL, message, data, error)
    }

    /**
     * Log de performance para medir tiempos
     */
    fun logPerformance(
        module: String,
        operation: String,
        durationMs: Long,
        success: Boolean,
        additionalData: Map<String, Any?> = emptyMap()
    ) {
        val data = additionalData.toMutableMap().apply {
            put("durationMs", durationMs)
            put("success", success)
        }

        logEvent(
            module = module,
            eventType = EventType.PERFORMANCE,
            eventName = operation,
            data = data
        )
    }

    /**
     * Log de acción de usuario
     */
    fun logUserAction(
        module: String,
        action: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        logEvent(
            module = module,
            eventType = EventType.USER_ACTION,
            eventName = action,
            data = data
        )
    }

    /**
     * Información del dispositivo
     */
    private fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,
            "androidVersion" to Build.VERSION.RELEASE,
            "sdkVersion" to Build.VERSION.SDK_INT.toString()
        )
    }

    /**
     * Log local en Logcat
     */
    private fun logLocal(tag: String, level: LogLevel, message: String, error: Throwable?) {
        val localTag = "$TAG:$tag"
        when (level) {
            LogLevel.DEBUG -> Log.d(localTag, message, error)
            LogLevel.INFO -> Log.i(localTag, message, error)
            LogLevel.WARNING -> Log.w(localTag, message, error)
            LogLevel.ERROR -> Log.e(localTag, message, error)
            LogLevel.CRITICAL -> Log.wtf(localTag, message, error)
        }
    }
}

/**
 * Extensiones para facilitar el uso
 */
object Logger {
    private var logger: RemoteLogger? = null

    fun init(context: Context) {
        logger = RemoteLogger.getInstance(context)
    }

    fun get(): RemoteLogger {
        return logger ?: throw IllegalStateException("Logger not initialized. Call Logger.init(context) first")
    }
}

// Extension functions para facilitar el uso
fun RemoteLogger.logSaleError(
    saleId: String,
    clientName: String,
    errorMessage: String,
    validationErrors: List<String> = emptyList(),
    additionalData: Map<String, Any?> = emptyMap()
) {
    val data = mutableMapOf<String, Any?>(
        "saleId" to saleId,
        "clientName" to clientName,
        "errorMessage" to errorMessage,
        "validationErrors" to validationErrors
    )
    data.putAll(additionalData)

    error(
        module = "SALES",
        action = "CREATE_SALE",
        message = errorMessage,
        data = data
    )
}

fun RemoteLogger.logCartAction(
    action: String,
    productId: Int? = null,
    quantity: Int? = null,
    additionalData: Map<String, Any?> = emptyMap()
) {
    val data = mutableMapOf<String, Any?>()
    productId?.let { data["productId"] = it }
    quantity?.let { data["quantity"] = it }
    data.putAll(additionalData)

    logUserAction(
        module = "CART",
        action = action,
        data = data
    )
}

fun RemoteLogger.logAuthEvent(
    event: String,
    success: Boolean,
    errorMessage: String? = null
) {
    val data = mutableMapOf<String, Any?>(
        "success" to success
    )
    errorMessage?.let { data["errorMessage"] = it }

    logEvent(
        module = "AUTH",
        eventType = RemoteLogger.EventType.SYSTEM_EVENT,
        eventName = event,
        data = data
    )
}