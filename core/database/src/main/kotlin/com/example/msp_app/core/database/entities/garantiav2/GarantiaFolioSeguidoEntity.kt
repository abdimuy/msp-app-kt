package com.example.msp_app.core.database.entities.garantiav2

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un folio de garantía que este teléfono abrió o tocó, para la bandeja de
 * "mis folios" del módulo v2.
 *
 * ## Por qué existe si el folio vive en el servidor
 *
 * `GET /v2/garantias` no tiene filtro por usuario: el servidor no puede decir
 * "los folios de este cobrador". La bandeja se arma con lo que este teléfono
 * conoce, y esta tabla es esa memoria. El folio completo se lee después del
 * servidor por [FOLIO_ID]; esta fila sólo guarda lo mínimo para listarlo y
 * para sobrevivir sin señal.
 *
 * ## [ID] es local y [FOLIO_ID] es del servidor
 *
 * [ID] lo acuña el teléfono al abrir el folio y **nunca viaja como `id`**. Un
 * folio abierto sin señal todavía no tiene [FOLIO_ID]: los comandos que
 * dependen de él guardan [ID] como referencia y esperan a que el servidor
 * responda. Confundir los dos es el defecto que esta tabla existe para
 * impedir.
 *
 * ## [ESTADO_LOCAL] no es el estado del folio
 *
 * El estado real (etapa, dictamen, desenlace) lo decide el servidor. Acá sólo
 * se distingue lo que el teléfono sabe sin preguntar: `pendiente_envio`
 * (abierto sin señal), `abierto` (el servidor ya devolvió [FOLIO_ID]) y
 * `rechazado` (algún comando de este folio no se reintenta).
 */
@Entity(
    tableName = "garantia_folios_seguidos",
    indices = [
        Index(value = ["FOLIO_ID"], unique = true),
        Index(value = ["ESTADO_LOCAL"]),
        Index(value = ["ABIERTO_EN"])
    ]
)
data class GarantiaFolioSeguidoEntity(
    /** UUID local. Nunca se envía como `id` del folio. */
    @PrimaryKey val ID: String,
    /** `id` del folio en el servidor; `NULL` mientras no se haya abierto allá. */
    @ColumnInfo(name = "FOLIO_ID") val FOLIO_ID: String? = null,
    /** `piso` o `cliente` — el origen con el que se abrió. */
    @ColumnInfo(name = "ORIGEN") val ORIGEN: String,
    @ColumnInfo(name = "CLIENTE_ID") val CLIENTE_ID: Int? = null,
    @ColumnInfo(name = "VENTA_ID") val VENTA_ID: Int? = null,
    /** `pendiente_envio`, `abierto` o `rechazado`. */
    @ColumnInfo(name = "ESTADO_LOCAL") val ESTADO_LOCAL: String,
    /** Instante de apertura en el teléfono, formato de cable de `AppTime`. */
    @ColumnInfo(name = "ABIERTO_EN") val ABIERTO_EN: String,
    @ColumnInfo(name = "ACTUALIZADO_EN") val ACTUALIZADO_EN: String,
    @ColumnInfo(name = "ULTIMO_ERROR") val ULTIMO_ERROR: String? = null
)
