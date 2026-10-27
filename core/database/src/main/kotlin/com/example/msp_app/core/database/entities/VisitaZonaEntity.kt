package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una visita de la zona tal como la entrega el servidor por el canal de bajada
 * (`/v2/visitas/sync/zona/{zona_id}`): la agenda que el cobrador LEE, no lo
 * que captura.
 *
 * ## Tabla aparte de lo capturado (I8, spec visitas §5.4.8, E-APP-080)
 *
 * Lo bajado vive aquí y nunca en la tabla de salida de las visitas propias: su
 * insert es `REPLACE` por id, y una fila bajada con el id de una visita propia
 * todavía no enviada pisaría lo no enviado. La derivación (Tarea 17) une las dos
 * por id y, ante el mismo id, gana lo local. Un resync (generación, cambio de
 * zona, reinstalación) borra esta tabla sin riesgo: el servidor la vuelve a
 * mandar.
 *
 * Solo la escribe `VisitaZonaDao`. Las columnas anulables son las que el
 * servidor puede omitir (Gson deja en nulo lo ausente).
 *
 * [UPDATED_AT] es el texto del servidor **tal cual**, sin reformatear: el
 * inventario (digest) lo hashea, y un reformateo daría un hash distinto del
 * del servidor.
 */
@Entity(
    tableName = "visita_zona",
    indices = [
        Index(value = ["ZONA_CLIENTE_ID"]),
        Index(value = ["IMPTE_DOCTO_CC_ID"]),
        Index(value = ["CLIENTE_ID"])
    ]
)
data class VisitaZonaEntity(
    @PrimaryKey val ID: String,
    val CLIENTE_ID: Int,
    val COBRADOR_ID: Int?,
    val COBRADOR: String?,
    val FECHA: String,
    val TIPO_VISITA: String,
    val ZONA_CLIENTE_ID: Int,
    val IMPTE_DOCTO_CC_ID: Int?,
    val NOTA: String?,
    val PROMESA_VENTA_ID: Int?,
    /** `yyyy-MM-dd`, fecha civil sin zona. */
    val PROMESA_FECHA: String?,
    /** Centavos enteros, nunca flotante. */
    val PROMESA_MONTO_CENTAVOS: Long?,
    /** `yyyy-MM-dd`, fecha civil sin zona. */
    val CITA_FECHA: String?,
    /** `HH:mm`, hora civil sin zona. */
    val CITA_HORA: String?,
    val UPDATED_AT: String
)
