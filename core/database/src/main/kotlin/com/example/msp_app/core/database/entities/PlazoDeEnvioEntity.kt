package com.example.msp_app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * El plazo de retencion del envio de una captura (abono, condonacion o visita): hasta cuando el
 * worker puede esperar una ubicacion antes de mandarla (E-APP-145, condicion K1 de la compuerta
 * ANTES c2).
 *
 * Tabla APARTE de `ubicacion_de_captura` a proposito: esa tabla se escribe con `INSERT IGNORE`,
 * asi que si el plazo viviera en ella bloquearia la ubicacion tardia (el insert devolveria -1).
 * Tampoco vive en `Payment`: esa tabla esta vedada para cambios de esquema.
 *
 * - `id`: el UUID de la captura (el mismo que viaja al servidor).
 * - `guardado_en_epoch_ms`: hora de PARED del guardado. Solo decide si hay algo que esperar al
 *   arrancar el worker; el tiempo que resta se mide con el reloj monotono (K4).
 * - `plazo_ms`: cuanto puede esperar el worker desde el guardado (30 000).
 *
 * Una captura sin fila aqui (pagos viejos, el barrido, un fallo al anotar) NO se retiene.
 */
@Entity(tableName = "plazo_de_envio")
data class PlazoDeEnvioEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "tipo") val tipo: String,
    @ColumnInfo(name = "guardado_en_epoch_ms") val guardadoEnEpochMs: Long,
    @ColumnInfo(name = "plazo_ms") val plazoMs: Long
) {
    companion object {
        /** Tope de la retencion: nunca se espera mas que esto, pase lo que pase con el reloj. */
        const val PLAZO_MS = 30_000L
    }
}
