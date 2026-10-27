package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una alarma local que debe existir (spec visitas §6.6; plan visitas P3,
 * Tareas 10 y 13; E-APP-121). Esta tabla es la fuente de verdad; las alarmas
 * de `AlarmManager` son su proyección y se reconstruyen desde aquí.
 *
 * - [ID] determinista: sha-256 hex de `"tipo|origen|instante"`, para que
 *   reprogramar sea idempotente.
 * - [INSTANTE] RFC3339 UTC en que suena.
 * - [TIPO] `promesa`, `cita` o `tarea`.
 * - [ORIGEN_ID] id de la visita o de la tarea que la origina.
 */
@Entity(tableName = "aviso_programado")
data class AvisoProgramadoEntity(
    @PrimaryKey val ID: String,
    val INSTANTE: String,
    val TIPO: String,
    val ORIGEN_ID: String
)
