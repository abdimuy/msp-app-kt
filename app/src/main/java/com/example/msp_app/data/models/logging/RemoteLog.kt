package com.example.msp_app.data.models.logging

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class RemoteLog(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val deviceInfo: String = "",
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "",
    val message: String = "",
    val stackTrace: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    val appVersion: String = "",
    val androidVersion: String = "",
    val deviceModel: String = ""
)

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

data class SaleErrorLog(
    val saleId: String = "",
    val clientName: String = "",
    val errorMessage: String = "",
    val errorType: String = "",
    val productCount: Int = 0,
    val hasImages: Boolean = false,
    val hasLocation: Boolean = false,
    val validationErrors: List<String> = emptyList()
)