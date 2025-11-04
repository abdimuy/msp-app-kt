package com.example.msp_app.data.local.entities

enum class SaleStatus {
    PENDIENTE,    // Recién creada, puede editarse
    COMPLETADA,   // Lista para enviar
    ENVIADA;      // Ya enviada al servidor, no editable

    companion object {
        fun fromString(value: String?): SaleStatus {
            return when (value?.uppercase()) {
                "COMPLETADA" -> COMPLETADA
                "ENVIADA" -> ENVIADA
                else -> PENDIENTE
            }
        }
    }
}