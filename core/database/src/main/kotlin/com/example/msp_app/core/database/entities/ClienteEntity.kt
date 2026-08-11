package com.example.msp_app.core.database.entities

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

/**
 * Proyección ligera de un cliente: solo su nombre, indexada por `CLIENTE_ID`. Alimenta el
 * enriquecimiento del nombre real de un cliente visitado (`RoomVisitsAdapter`,
 * `:feature:collectionReport`) sin traer la entidad `cliente` completa. Solo lectura — no es
 * una `@Entity`, no toca el schema.
 */
data class ClienteRefRow(
    val CLIENTE_ID: Int,
    val NOMBRE: String
)
