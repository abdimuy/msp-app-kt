package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Posición del canal de bajada de tareas por zona (spec visitas §6.2 y
 * §5.4.3; plan visitas P3, Tarea 10; E-APP-121). Una fila por zona. Misma
 * forma y mismas reglas que `visitas_sync_state`:
 *
 * - El cursor es un PAR (`COBRANZA-SYNC.md` §2): [CURSOR] es el `UPDATED_AT`
 *   y [AFTER_ID] el id (UUID) de la última fila aplicada. Donde se escribe uno
 *   se escribe el otro, en la misma transacción que la página; todo camino
 *   que deje [CURSOR] nulo deja [AFTER_ID] nulo.
 * - [EPOCH] es la generación `tareas` que este teléfono YA terminó de
 *   replicar; `NULL` = nunca terminó una (`COBRANZA-SYNC.md` §3).
 */
@Entity(tableName = "tareas_sync_state")
data class TareasSyncStateEntity(
    @PrimaryKey val ZONA: Int,
    val CURSOR: String?,
    val AFTER_ID: String?,
    val EPOCH: Int?
)
