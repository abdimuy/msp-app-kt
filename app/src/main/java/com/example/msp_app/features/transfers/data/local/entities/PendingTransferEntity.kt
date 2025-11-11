package com.example.msp_app.features.transfers.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for pending transfers (not yet synced to server)
 * Stores the complete transfer data for offline creation
 */
@Entity(tableName = "pending_transfers")
data class PendingTransferEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "almacen_origen_id")
    val almacenOrigenId: Int,

    @ColumnInfo(name = "almacen_destino_id")
    val almacenDestinoId: Int,

    @ColumnInfo(name = "fecha")
    val fecha: String,

    @ColumnInfo(name = "descripcion")
    val descripcion: String? = null,

    @ColumnInfo(name = "usuario")
    val usuario: String? = null,

    @ColumnInfo(name = "almacen_origen_nombre")
    val almacenOrigenNombre: String? = null,

    @ColumnInfo(name = "almacen_destino_nombre")
    val almacenDestinoNombre: String? = null,

    @ColumnInfo(name = "detalles_json")
    val detallesJson: String, // JSON serialized list of details

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
