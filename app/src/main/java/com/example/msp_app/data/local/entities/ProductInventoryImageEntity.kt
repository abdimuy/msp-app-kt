package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products_inventory_images",
    indices = [
        Index(value = ["ARTICULO_ID"]),
        Index(value = ["RUTA_LOCAL"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = ProductInventoryEntity::class,
            parentColumns = ["ARTICULO_ID"],
            childColumns = ["ARTICULO_ID"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProductInventoryImageEntity(
    @PrimaryKey val IMAGEN_ID: Int,
    val ARTICULO_ID: Int,
    val RUTA_LOCAL: String
)
