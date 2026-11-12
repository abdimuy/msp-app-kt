package com.example.msp_app.core.database.entities.garantiav2

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un comando de garantía encolado, listo para viajar tal como se guardó.
 *
 * ## Lo que esta fila congela (D3 del brief)
 *
 * El API responde 409 `warranty_idempotency_key_mismatch` a un reintento con
 * la misma [CLAVE] y **cualquier** campo distinto, incluida la hora escrita
 * con otro formato. Por eso el cuerpo, la hora y la clave se guardan
 * serializados **una sola vez**, al encolar, y el trabajador los reenvía sin
 * reconstruirlos desde el dominio.
 *
 * [CUERPO] es el texto JSON exacto del comando, o el JSON con los campos del
 * multipart (texto) y la ruta del archivo de la firma. [CONTENT_TYPE] es fijo
 * para multipart (`image/png` en la parte de la firma), no se deduce del
 * archivo.
 *
 * ## Por qué [RUTA] es una plantilla
 *
 * Un folio abierto sin señal todavía no tiene `id` del servidor, así que la
 * ruta no puede resolverse al encolar. [RUTA] guarda la plantilla con
 * `{folio}`, `{articulo}` y `{evento}`; el trabajador sustituye sólo cuando
 * el id ya está en [FOLIO_ID], [ARTICULO_ID] o [EVENTO_ID]. Los ids se
 * escriben una vez, cuando el servidor responde al comando del que dependen.
 *
 * ## Orden y bloqueo
 *
 * [ID] es autoincremental y define el orden dentro de un folio
 * ([FOLIO_REF]). Un comando no sale mientras otro anterior del mismo folio
 * siga `pendiente`; si uno queda `rechazado`, los que dependen de él se
 * detienen. Un comando encolado no se edita: si el usuario quiere otra cosa,
 * es otro comando con otra clave.
 */
@Entity(
    tableName = "garantia_comandos_pendientes",
    indices = [
        Index(value = ["CLAVE"], unique = true),
        Index(value = ["ESTADO"]),
        Index(value = ["FOLIO_REF"]),
        Index(value = ["FOLIO_ID"]),
        Index(value = ["ARTICULO_REF"]),
        Index(value = ["EVENTO_REF"])
    ]
)
data class GarantiaComandoPendienteEntity(
    /** Orden de la cola dentro de su folio. No viaja al servidor. */
    @PrimaryKey(autoGenerate = true) val ID: Long = 0,
    /** UUID generado una sola vez, al encolar. Viaja como `Idempotency-Key`. */
    @ColumnInfo(name = "CLAVE") val CLAVE: String,
    /** `abrir_folio`, `agregar_articulo`, `recoleccion` o `entrega`. */
    @ColumnInfo(name = "OPERACION") val OPERACION: String,
    @ColumnInfo(name = "METODO") val METODO: String,
    /** Ruta relativa con `{folio}`, `{articulo}` y `{evento}` si hacen falta. */
    @ColumnInfo(name = "RUTA") val RUTA: String,
    /** Cuerpo ya serializado; `NULL` sólo si el comando no lleva cuerpo. */
    @ColumnInfo(name = "CUERPO") val CUERPO: String? = null,
    /** `Content-Type` exacto del cuerpo; para multipart, el del boundary fijo. */
    @ColumnInfo(name = "CONTENT_TYPE") val CONTENT_TYPE: String? = null,
    /** Hora del dispositivo ya escrita en UTC con `Z`; no se recalcula. */
    @ColumnInfo(name = "DEVICE_CREATED_AT") val DEVICE_CREATED_AT: String,
    /** `pendiente`, `enviado` o `rechazado`. */
    @ColumnInfo(name = "ESTADO") val ESTADO: String,
    @ColumnInfo(name = "ULTIMO_ERROR") val ULTIMO_ERROR: String? = null,
    /** Referencia local del folio; ver [GarantiaFolioSeguidoEntity.ID]. */
    @ColumnInfo(name = "FOLIO_REF") val FOLIO_REF: String? = null,
    /** `id` del folio en el servidor, una vez que `abrir_folio` respondió. */
    @ColumnInfo(name = "FOLIO_ID") val FOLIO_ID: String? = null,
    /** Referencia local del artículo que este comando creó o movió. */
    @ColumnInfo(name = "ARTICULO_REF") val ARTICULO_REF: String? = null,
    @ColumnInfo(name = "ARTICULO_ID") val ARTICULO_ID: String? = null,
    /** Referencia local del evento que este comando creó. */
    @ColumnInfo(name = "EVENTO_REF") val EVENTO_REF: String? = null,
    @ColumnInfo(name = "EVENTO_ID") val EVENTO_ID: String? = null,
    @ColumnInfo(name = "INTENTOS") val INTENTOS: Int = 0,
    @ColumnInfo(name = "CREADO_EN") val CREADO_EN: String,
    @ColumnInfo(name = "ACTUALIZADO_EN") val ACTUALIZADO_EN: String
)
