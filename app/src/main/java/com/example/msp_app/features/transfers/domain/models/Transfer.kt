package com.example.msp_app.features.transfers.domain.models

import java.time.LocalDateTime

/**
 * Domain model for a warehouse transfer
 * Clean, business-logic focused representation
 */
data class Transfer(
    val doctoInId: Int,
    val almacenOrigenId: Int,
    val almacenDestinoId: Int,
    val fecha: LocalDateTime,
    val descripcion: String?,
    val folio: String?,
    val usuario: String?,
    val almacenOrigenNombre: String?,
    val almacenDestinoNombre: String?,
    val totalProductos: Int,
    val costoTotal: Double,
    val aplicado: Boolean,
    val sincronizado: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    /**
     * Returns a user-friendly display name for the source warehouse
     */
    fun getDisplaySourceWarehouse(): String {
        return almacenOrigenNombre ?: "Almacén #$almacenOrigenId"
    }

    /**
     * Returns a user-friendly display name for the destination warehouse
     */
    fun getDisplayDestinationWarehouse(): String {
        return almacenDestinoNombre ?: "Almacén #$almacenDestinoId"
    }

    /**
     * Returns a formatted transfer description
     */
    fun getFormattedDescription(): String {
        return "${getDisplaySourceWarehouse()} → ${getDisplayDestinationWarehouse()}"
    }

    /**
     * Checks if the transfer is pending synchronization
     */
    fun isPendingSync(): Boolean = !sincronizado

    /**
     * Checks if the transfer is applied/completed
     */
    fun isApplied(): Boolean = aplicado
}
