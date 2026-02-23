package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cliente",
    indices = [Index(value = ["NOMBRE"])]
)
class ClienteEntity(
    @PrimaryKey val CLIENTE_ID: Int,
    val NOMBRE: String,
    val ESTATUS: String,
    val CAUSA_SUSP: String?
)
