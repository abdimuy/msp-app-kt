package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "garantia_eventos",
    indices = [
        Index(value = ["GARANTIA_ID"]),
        Index(value = ["FECHA_EVENTO"])
    ]
)
data class GuaranteeEventEntity(
    @PrimaryKey val ID: String,
    val GARANTIA_ID: String,
    val TIPO_EVENTO: String,
    val FECHA_EVENTO: String,
    val COMENTARIO: String?,
    val ENVIADO: Int
)
