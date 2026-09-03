package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
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
 * Una sola tabla con `(TIPO, PADRE_ID)` no puede llevar llave foránea real —
 * el padre depende del valor de una columna — y las dos convenciones de
 * storage key del servidor son distintas. El repo ya resolvió esto igual:
 * `garantia_imagenes` y `sale_image` son tablas separadas, una por padre. Dos
 * tablas cuestan un `CREATE TABLE` extra y compran integridad referencial y
 * un índice por padre.
 *
 * `Payment` NO recibe ninguna columna nueva en esta migración: la tabla del
 * dinero se queda exactamente como está.
 */
@Entity(
    tableName = "pago_imagenes",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["ID"],
            childColumns = ["PAGO_ID"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["PAGO_ID"]),
        Index(value = ["SUBIDA_EN"])
    ]
)
data class PaymentImageEntity(
    /** UUID generado en el teléfono. Viaja como `id_<n>` en el multipart. */
    @PrimaryKey val ID: String,
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
