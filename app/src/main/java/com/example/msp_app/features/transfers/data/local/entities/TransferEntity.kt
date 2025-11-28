package com.example.msp_app.features.transfers.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for transfers
 * Represents the main transfer document
 */
@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey
    @ColumnInfo(name = "docto_in_id")
    val doctoInId: Int,

    @ColumnInfo(name = "almacen_origen_id")
    val almacenOrigenId: Int,

    @ColumnInfo(name = "almacen_destino_id")
    val almacenDestinoId: Int,

    @ColumnInfo(name = "fecha")
    val fecha: String,

    @ColumnInfo(name = "descripcion")
    val descripcion: String? = null,

    @ColumnInfo(name = "folio")
    val folio: String? = null,

    @ColumnInfo(name = "usuario")
    val usuario: String? = null,

    @ColumnInfo(name = "aplicado")
    val aplicado: String? = null,

    @ColumnInfo(name = "almacen_origen_nombre")
    val almacenOrigenNombre: String? = null,

    @ColumnInfo(name = "almacen_destino_nombre")
    val almacenDestinoNombre: String? = null,

    @ColumnInfo(name = "total_productos")
    val totalProductos: Int = 0,

    @ColumnInfo(name = "costo_total")
    val costoTotal: Double = 0.0,

    @ColumnInfo(name = "sincronizado")
    val sincronizado: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
