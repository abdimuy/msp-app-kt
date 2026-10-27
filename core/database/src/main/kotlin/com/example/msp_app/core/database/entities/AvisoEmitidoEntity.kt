package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro de la notificación "Visita asignada" ya mostrada (spec visitas
 * §6.5; plan visitas P3, Tareas 10 y 12; E-APP-121). Hace idempotente el
 * aviso: el mismo [ID] nunca notifica dos veces, llegue la tarea por push,
 * por el periódico o por un resync.
 *
 * - [ID] = id de la tarea.
 * - [EMITIDO_EN] RFC3339 UTC del momento en que se mostró.
 */
@Entity(tableName = "aviso_emitido")
data class AvisoEmitidoEntity(
    @PrimaryKey val ID: String,
    val EMITIDO_EN: String
)
