package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "products",
    indices = [
        Index(value = ["DOCTO_PV_ID"]),
        Index(value = ["FOLIO"]),
        Index(value = ["ARTICULO_ID"])
    ])
data class ProductEntity(
    @PrimaryKey val DOCTO_PV_DET_ID: Int,
    val DOCTO_PV_ID: Int,
    val FOLIO: String,
    val ARTICULO_ID: Int,
    val ARTICULO: String,
    val CANTIDAD: Int,
    val PRECIO_UNITARIO_IMPTO: Double,
    val PRECIO_TOTAL_NETO: Double,
    val POSICION: Int
)
