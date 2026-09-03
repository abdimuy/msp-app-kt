package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
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
 * FK a `Visit` con `ON DELETE CASCADE`, igual que [LocalSaleImageEntity]
 * sobre `local_sale`: cuando `VisitDao.deleteUploadedVisits` poda una visita
 * ya confirmada, sus filas de imagen se van con ella en vez de quedar
 * apuntando a un padre inexistente. La poda solo alcanza visitas con
 * `GUARDADO_EN_MICROSIP = 1`, es decir, ya subidas con sus fotos.
 */
@Entity(
    tableName = "visita_imagenes",
    foreignKeys = [
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["ID"],
            childColumns = ["VISITA_ID"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["VISITA_ID"]),
        Index(value = ["SUBIDA_EN"])
    ]
)
data class VisitImageEntity(
    /** UUID generado en el teléfono. Viaja como `id_<n>` en el multipart. */
    @PrimaryKey val ID: String,
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
