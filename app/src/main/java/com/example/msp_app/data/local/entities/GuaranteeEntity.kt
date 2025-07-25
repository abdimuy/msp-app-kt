package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "garantias",
    indices = [
        Index(value = ["EXTERNAL_ID"], unique = true),
        Index(value = ["DOCTO_CC_ID"])
    ]
)
data class GuaranteeEntity(
    @PrimaryKey(autoGenerate = true) val ID: Int = 0,
    val EXTERNAL_ID: String,
    val DOCTO_CC_ID: Int,
    val ESTADO: String,
    val DESCRIPCION: String,
    val OBSERVACIONES: String?,
    val UPLOADED: Int,
    val FECHA_SOLICITUD: String
)
