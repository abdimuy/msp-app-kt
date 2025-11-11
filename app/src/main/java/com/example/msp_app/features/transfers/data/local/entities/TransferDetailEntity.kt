package com.example.msp_app.features.transfers.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for transfer details (products/movements)
 */
@Entity(
    tableName = "transfer_details",
    foreignKeys = [
        ForeignKey(
            entity = TransferEntity::class,
            parentColumns = ["docto_in_id"],
            childColumns = ["docto_in_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["docto_in_id"])]
)
data class TransferDetailEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "docto_in_id")
    val doctoInId: Int,

    @ColumnInfo(name = "articulo_id")
    val articuloId: Int,

    @ColumnInfo(name = "clave_articulo")
    val claveArticulo: String,

    @ColumnInfo(name = "articulo_nombre")
    val articuloNombre: String? = null,

    @ColumnInfo(name = "descripcion1")
    val descripcion1: String? = null,

    @ColumnInfo(name = "descripcion2")
    val descripcion2: String? = null,

    @ColumnInfo(name = "unidades")
    val unidades: Int,

    @ColumnInfo(name = "costo_unitario")
    val costoUnitario: Double,

    @ColumnInfo(name = "costo_total")
    val costoTotal: Double,

    @ColumnInfo(name = "tipo_movimiento")
    val tipoMovimiento: String, // "S" (Salida) o "E" (Entrada)

    @ColumnInfo(name = "movto_id")
    val movtoId: Int? = null
)
