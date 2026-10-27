package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una tarea de oficina ("visitar esta venta") bajada de la zona por el canal
 * `/v2/visitas/sync/tareas/zona/{zona_id}` (spec visitas §6.1 y §6.2; plan
 * visitas P3, Tareas 10 y 11; E-APP-121). Tabla de LECTURA: solo la escribe
 * `TareaZonaDao` con lo que manda el servidor.
 *
 * El estado (pendiente, atendida, no atendida, cancelada) **no** se guarda:
 * se deriva (spec §6.3). [CANCELADA_AT] es el hecho de la cancelación, no un
 * estado.
 *
 * - [VENTA_ID] = `DOCTO_CC_ACR_ID` de la venta.
 * - [FECHA] `yyyy-MM-dd`, fecha civil sin zona.
 * - [HORA] `HH:mm`, hora civil sin zona; `null` = sin hora.
 * - [NOTA] hasta 500 caracteres; sí viaja a la zona (decisión del dueño,
 *   2026-10-19).
 * - [REEMPLAZA_A] id de la tarea que esta reemplaza ("editar = cancelar +
 *   asignar otra").
 * - [CREATED_AT], [UPDATED_AT], [CANCELADA_AT]: RFC3339 tal como los manda el
 *   servidor.
 */
@Entity(
    tableName = "tarea_zona",
    indices = [
        Index(value = ["ZONA_CLIENTE_ID"]),
        Index(value = ["VENTA_ID"])
    ]
)
data class TareaZonaEntity(
    @PrimaryKey val ID: String,
    val VENTA_ID: Int,
    val CLIENTE_ID: Int,
    val ZONA_CLIENTE_ID: Int,
    val FECHA: String,
    val HORA: String?,
    val NOTA: String?,
    val REEMPLAZA_A: String?,
    val CREATED_AT: String,
    val UPDATED_AT: String,
    val CANCELADA_AT: String?
)
