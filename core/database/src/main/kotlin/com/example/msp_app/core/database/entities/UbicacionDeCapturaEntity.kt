package com.example.msp_app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * La ubicacion de una captura (abono, condonacion o visita) y el estado de su
 * envio al servidor (E-APP-145).
 *
 * Tabla APARTE, sin llave foranea y sin tocar la tabla de la captura: el dato
 * de ubicacion no se pierde cuando el teléfono reemplaza la fila local por la
 * del servidor, y ningun consulta de la captura necesita conocerla.
 *
 * - `id`: el UUID de la captura (el mismo que viaja al servidor), no el
 *   numerico que el servidor asigna despues.
 * - `fix_at_ms`: instante UTC del fix, en milisegundos de epoch.
 * - `origen`: [ORIGEN_ANTES] si el fix se tomo antes de guardar, o
 *   [ORIGEN_DESPUES] si llego despues.
 * - `estado_envio`: [PENDIENTE] hasta que el servidor la recibe; [ENVIADA] o
 *   [DESCARTADA] son finales.
 */
@Entity(tableName = "ubicacion_de_captura")
data class UbicacionDeCapturaEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "tipo") val tipo: String,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lng") val lng: Double,
    @ColumnInfo(name = "precision_m") val precisionM: Double,
    @ColumnInfo(name = "fix_at_ms") val fixAtMs: Long,
    @ColumnInfo(name = "origen") val origen: String,
    @ColumnInfo(name = "estado_envio") val estadoEnvio: String,
    @ColumnInfo(name = "intentos") val intentos: Int,
    @ColumnInfo(name = "creada_en") val creadaEn: Long
) {
    companion object {
        const val TIPO_PAGO = "pago"
        const val TIPO_VISITA = "visita"

        const val ORIGEN_ANTES = "antes"
        const val ORIGEN_DESPUES = "despues"

        const val PENDIENTE = "pendiente"
        const val ENVIADA = "enviada"
        const val DESCARTADA = "descartada"
    }
}
