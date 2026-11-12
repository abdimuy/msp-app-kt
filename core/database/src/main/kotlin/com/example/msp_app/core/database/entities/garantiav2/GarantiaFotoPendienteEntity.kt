package com.example.msp_app.core.database.entities.garantiav2

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una foto de la recolección o de la entrega, en espera de subir a
 * `POST /v2/garantias/{folio}/eventos/{evento}/imagenes`.
 *
 * ## La clave es la de la foto, no la del comando
 *
 * [CLAVE] se genera **una vez, al tomar la foto**, y es también el `id` de la
 * imagen en el servidor. Reintentar la subida usa esa misma clave; si se
 * regenerara, el servidor crearía una segunda imagen para la misma foto. Por
 * eso es la llave primaria local y no un autoincremental.
 *
 * ## La foto espera a su evento
 *
 * [EVENTO_REF] es la referencia local del evento de recolección; [EVENTO_ID]
 * se escribe cuando ese comando respondió y el servidor devolvió el evento.
 * Mientras falte, la foto no puede subir. El archivo local se borra sólo
 * cuando la subida responde 2xx: [SUBIDA_EN] es la marca que lo autoriza.
 *
 * Sin llave foránea a propósito, igual que [VisitImageEntity]: una cascada
 * sobre un `INSERT OR REPLACE` borraría fotos en silencio, y una fila
 * huérfana es visible y limpiable.
 */
@Entity(
    tableName = "garantia_fotos_pendientes",
    indices = [
        Index(value = ["ESTADO"]),
        Index(value = ["FOLIO_REF"]),
        Index(value = ["FOLIO_ID"]),
        Index(value = ["EVENTO_REF"]),
        Index(value = ["EVENTO_ID"]),
        Index(value = ["COMANDO_ID"])
    ]
)
data class GarantiaFotoPendienteEntity(
    /** UUID de la imagen; viaja como `Idempotency-Key` y es su `id` remoto. */
    @PrimaryKey val CLAVE: String,
    /** Comando de recolección o entrega que la produjo. */
    @ColumnInfo(name = "COMANDO_ID") val COMANDO_ID: Long? = null,
    @ColumnInfo(name = "FOLIO_REF") val FOLIO_REF: String? = null,
    @ColumnInfo(name = "FOLIO_ID") val FOLIO_ID: String? = null,
    @ColumnInfo(name = "EVENTO_REF") val EVENTO_REF: String? = null,
    @ColumnInfo(name = "EVENTO_ID") val EVENTO_ID: String? = null,
    /** Ruta del archivo ya comprimido en `filesDir`. */
    @ColumnInfo(name = "RUTA_ARCHIVO") val RUTA_ARCHIVO: String,
    /** MIME del archivo; el servidor valida contra su whitelist. */
    @ColumnInfo(name = "MIME") val MIME: String,
    /** `pendiente`, `enviada` o `rechazada`. */
    @ColumnInfo(name = "ESTADO") val ESTADO: String,
    @ColumnInfo(name = "INTENTOS") val INTENTOS: Int = 0,
    @ColumnInfo(name = "ULTIMO_ERROR") val ULTIMO_ERROR: String? = null,
    @ColumnInfo(name = "CREADO_EN") val CREADO_EN: String,
    @ColumnInfo(name = "ACTUALIZADO_EN") val ACTUALIZADO_EN: String,
    /** Instante en que el servidor confirmó la imagen; `NULL` = no ha subido. */
    @ColumnInfo(name = "SUBIDA_EN") val SUBIDA_EN: String? = null
)
