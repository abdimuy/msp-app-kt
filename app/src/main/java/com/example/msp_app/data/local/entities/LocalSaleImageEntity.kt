package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_image",
    foreignKeys = [ForeignKey(
        entity = LocalSaleEntity::class,
        parentColumns = ["LOCAL_SALE_ID"],
        childColumns = ["LOCAL_SALE_ID"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["LOCAL_SALE_ID"]),
        Index(value = ["FECHA_SUBIDA"])
    ]
)
class LocalSaleImageEntity(
    @PrimaryKey val LOCAL_SALE_IMAGE_ID: String,
    val LOCAL_SALE_ID: String,
    val IMAGE_URI: String,
    val FECHA_SUBIDA: String
)