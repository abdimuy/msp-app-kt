package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_inventory",
    indices = [
        Index(
            value = ["ARTICULO_ID"],
        )]
)
data class ProductInventoryEntity(
    @PrimaryKey val ARTICULO_ID: Int,
    val ARTICULO: String,
    val EXISTENCIAS: Int,
    val LINEA_ARTICULO_ID: Int,
    val LINEA_ARTICULO: String,
    val PRECIOS: String
)
