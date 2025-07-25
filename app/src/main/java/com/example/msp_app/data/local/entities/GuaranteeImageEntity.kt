package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "garantia_imagenes",
    indices = [
        Index(value = ["GARANTIA_ID"])
    ]
)
data class GuaranteeImageEntity(
    @PrimaryKey val ID: String,
    val GARANTIA_ID: Int,
    val IMG_PATH: String,
    val IMG_MIME: String,
    val IMG_DESC: String?,
    val FECHA_SUBIDA: String
)
