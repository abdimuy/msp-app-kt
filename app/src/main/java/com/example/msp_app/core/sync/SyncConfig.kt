package com.example.msp_app.core.sync

import androidx.work.NetworkType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Configuración para sincronización de una entidad
 */
data class SyncConfig(
    /** Identificador único del tipo de entidad */
    val entityType: String,

    /** Nombre del worker único para WorkManager */
    val workerNamePrefix: String = "sync_${entityType.lowercase()}",

    /** Tipo de red requerida */
    val networkType: NetworkType = NetworkType.CONNECTED,

    /** Número máximo de reintentos */
    val maxRetries: Int = 3,

    /** Delay inicial entre reintentos (exponential backoff) */
    val initialRetryDelay: Duration = 30.seconds,

    /** Delay máximo entre reintentos */
    val maxRetryDelay: Duration = 5.minutes,

    /** Si debe procesar imágenes/archivos adjuntos */
    val hasAttachments: Boolean = false,

    /** Códigos HTTP que no deberían reintentar */
    val nonRetryableErrorCodes: Set<Int> = setOf(400, 401, 403, 404, 422),

    /** Códigos HTTP que indican conflicto */
    val conflictErrorCodes: Set<Int> = setOf(409),

    /** Habilitar logging detallado */
    val enableLogging: Boolean = true,

    /** Tag para logging */
    val logTag: String = "OfflineSync"
) {
    companion object {
        /**
         * Configuración simple para entidades sin archivos
         */
        fun simple(entityType: String) = SyncConfig(
            entityType = entityType
        )

        /**
         * Configuración para entidades con imágenes/archivos
         */
        fun withAttachments(entityType: String) = SyncConfig(
            entityType = entityType,
            hasAttachments = true,
            maxRetries = 5
        )

        /**
         * Configuración para operaciones críticas
         */
        fun critical(entityType: String) = SyncConfig(
            entityType = entityType,
            maxRetries = 10,
            initialRetryDelay = 10.seconds
        )
    }
}
