package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un comprobante fotográfico de un pago, en espera de viajar dentro del
 * multipart de `POST /v2/pagos`.
 *
 * Mismo razonamiento que [VisitImageEntity], sobre el otro contrato: el
 * servidor declara `Imagen []huma.FormFile` — *"0..N comprobantes"*
 * (`dto_pago_recibido.go:107`) — y exige un `id_<n>` por parte, con clave de
 * storage `pagos/<pago_id>/<imagen_id><ext>`. [ID] es ese `id_<n>`.
 *
 * ## Por qué DOS tablas de imagen y no una polimórfica
 *
 * Una sola tabla con `(TIPO, PADRE_ID)` no puede llevar una referencia tipada
 * por padre —el padre dependería del valor de una columna— y las dos
 * convenciones de storage key del servidor son distintas. El repo ya resolvió
 * esto igual: `garantia_imagenes` y `sale_image` son tablas separadas, una por
 * padre.
 *
 * ## [PAGO_ID] es una referencia SUELTA — sin llave foránea, a propósito
 *
 * Una FK a `Payment(ID)` con `ON DELETE CASCADE` **borraría comprobantes
 * durante la sincronización normal**, sin error y sin rastro. La fila de un
 * pago capturado en el teléfono se borra y se vuelve a crear con otra llave
 * como parte de la rutina:
 *
 * - `CobranzaSyncManager.mergePagos` llama a `PaymentDao.deleteByIDs` en
 *   cuanto el servidor devuelve el `pago_recibido_id` del gemelo UUID, y
 *   reinserta la fila canónica bajo el `IMPTE_DOCTO_CC_ID` numérico. Un
 *   comprobante cuya subida no hubiera terminado cuando cae el tick de 30 s
 *   se perdería.
 * - `PaymentsLocalDataSource.saveAll` corre `PaymentDao.deleteUploaded()`
 *   antes de reinsertar en cada sincronización del catálogo: se llevaría
 *   también el registro de [SUBIDA_EN], que es lo que decide cuándo el
 *   archivo local puede borrarse, dejando archivos huérfanos sin registro.
 *
 * **El trade es deliberado**, y es el mismo de [VisitImageEntity]: una fila
 * huérfana es visible y limpiable; una foto borrada en silencio no se
 * recupera. Y quitar una FK en SQLite obliga a **recrear la tabla** —una
 * migración no aditiva, prohibida por este plan—, así que se decide aquí.
 *
 * **Quién limpia:** el camino de subida de la **Task 22**, el único que sabe
 * que el servidor confirmó la imagen (stampa [SUBIDA_EN]) y que el archivo
 * local puede irse con su fila. Como barrido de respaldo, esa misma tarea
 * debe poder borrar las filas cuyo `PAGO_ID` ya no exista en `Payment` — con
 * el cuidado de no confundir "el pago fue re-llavado por el sync" con "el
 * pago se fue": el re-llavado es rutina, así que el barrido debe correr por
 * antigüedad y no en el mismo tick del merge.
 *
 * `Payment` NO recibe ninguna columna nueva en esta migración: la tabla del
 * dinero se queda exactamente como está.
 */
@Entity(
    tableName = "pago_imagenes",
    indices = [
        Index(value = ["PAGO_ID"]),
        Index(value = ["SUBIDA_EN"])
    ]
)
data class PaymentImageEntity(
    /** UUID generado en el teléfono. Viaja como `id_<n>` en el multipart. */
    @PrimaryKey val ID: String,
    /** `Payment.ID`. Referencia suelta: ver el KDoc de la clase. */
    val PAGO_ID: String,
    /** `content://` o ruta del archivo local ya comprimido. */
    val URI: String,
    /** MIME real del archivo; el servidor valida contra su whitelist. */
    val MIME: String,
    /** Viaja como `descripcion_<n>`. Opcional en el contrato. */
    val DESCRIPCION: String? = null,
    /** Posición `n` con la que se arma el multipart. */
    val ORDEN: Int = 0,
    /** Instante de captura, formato de cable de `AppTime`. */
    val CREADA_EN: String,
    /** Instante en que el servidor la confirmó; `NULL` = todavía no sube. */
    val SUBIDA_EN: String? = null
)
