package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_sale",
    indices = [
        androidx.room.Index(value = ["FECHA_VENTA"])
    ]
)
class LocalSaleEntity(
    @PrimaryKey val LOCAL_SALE_ID: String,
    val NOMBRE_CLIENTE: String,
    val FECHA_VENTA: String,
    val LATITUD: Double,
    val LONGITUD: Double,
    val DIRECCION: String
)