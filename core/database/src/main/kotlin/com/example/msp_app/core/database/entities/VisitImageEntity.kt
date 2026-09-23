package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un comprobante fotográfico de una visita, en espera de viajar dentro del
 * multipart de `POST /v2/visitas` (Task 9).
 *
 * ## Por qué es tabla hija y no una columna
 *
 * El contrato del servidor admite **0..N** comprobantes por visita
 * (`MSP_VISITAS_IMAGENES`, migración `000063`, con FK a `MSP_VISITAS`), y el
 * handler lee las partes repetidas `imagen` + `id_<n>` + `descripcion_<n>`.
 * Una columna con una sola referencia contradiría ese contrato desde el
 * primer comprobante extra; el molde local ya existe en el repo
 * ([GuaranteeImageEntity], [LocalSaleImageEntity]) y es el mismo.
 *
 * ## [ID] es el `id_<n>`, y por eso es lo que hace idempotente el reintento
 *
 * El servidor **exige** un `id_<n>` por cada parte `imagen`: con la clave de
 * storage `visitas/<visita_id>/<imagen_id><ext>` determinista, reintentar la
 * misma visita con las mismas fotos no crea una segunda fila ni huerfana el
 * blob (Task 9, §4). Ese UUID lo genera el teléfono al capturar la foto y NO
 * cambia entre reintentos — de ahí que sea la llave primaria local y no un
 * autoincremental.
 *
 * [ORDEN] preserva la posición `n` con la que se armó el multipart, para que
 * un reintento reconstruya las mismas parejas `imagen`/`id_<n>` en el mismo
 * orden. [SUBIDA_EN] queda en `NULL` hasta que el servidor confirma la
 * imagen; es lo que permite después borrar el archivo local sin adivinar.
 *
 * ## [VISITA_ID] es una referencia SUELTA — sin llave foránea, a propósito
 *
 * La versión anterior de esta tabla colgaba una FK de `Visit(ID)` con
 * `ON DELETE CASCADE`, y eso borraba fotos en silencio. `VisitDao.insertVisit`
 * usa `OnConflictStrategy.REPLACE`, y un `INSERT OR REPLACE` **borra la fila
 * antes de reinsertarla**: la cascada se dispara y se lleva los comprobantes
 * de una visita que el usuario acaba de volver a guardar. Hoy está latente
 * —ningún flujo reinserta una visita existente— pero las Tasks 19 y 23
 * agregan exactamente eso: un camino de guardado que además adjunta fotos.
 * Es el mismo razonamiento que dejó a [VisitRecommendationEntity] sin FK.
 *
 * **El trade es deliberado:** sin FK puede quedar una fila huérfana (un
 * comprobante cuya visita ya no está). Una fila huérfana es **visible y
 * limpiable** por el código que sabe cuándo el archivo puede irse; una foto
 * borrada en silencio no se recupera. Quitar una FK en SQLite obliga a
 * **recrear la tabla** —una migración no aditiva, que este plan prohíbe—, así
 * que la decisión se toma aquí o no se toma.
 *
 * **Quién limpia:** el camino de subida de la **Task 23**. Es el único que
 * sabe que el servidor ya confirmó la imagen (stampa [SUBIDA_EN]) y que por
 * tanto el archivo local puede borrarse junto con su fila. Como barrido de
 * respaldo, esa misma tarea debe poder borrar las filas cuyo `VISITA_ID` ya
 * no exista en `Visit`.
 *
 * **Constraint que la Task 23 debe honrar:** una visita solo puede podarse
 * (`VisitDao.deleteUploadedVisits`, `GUARDADO_EN_MICROSIP = 1`) cuando sus
 * comprobantes ya subieron. Eso se cumple *si y solo si* las fotos viajan en
 * el MISMO request que la visita. No es un hecho garantizado por el esquema:
 * bajo la convivencia JSON obligatoria (Ruling E) una visita puede quedar
 * confirmada por `GET /v2/visitas/by-ids` con fotos todavía pendientes. La
 * Task 23 tiene que impedir que se marque como subida una visita con
 * comprobantes sin [SUBIDA_EN].
 */
@Entity(
    tableName = "visita_imagenes",
    indices = [
        Index(value = ["VISITA_ID"]),
        Index(value = ["SUBIDA_EN"])
    ]
)
data class VisitImageEntity(
    /** UUID generado en el teléfono. Viaja como `id_<n>` en el multipart. */
    @PrimaryKey val ID: String,
    /** `Visit.ID`. Referencia suelta: ver el KDoc de la clase. */
    val VISITA_ID: String,
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
