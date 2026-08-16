package com.example.msp_app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the incremental-sync cursor for each cobranza resource the app
 * polls from the v2 Go backend. One row per resource (`ventas`, `pagos`).
 *
 * - `cursor` is the RFC3339 `max_updated_at` value the backend returned on
 *   the last successful page. Null when the resource has never been
 *   synced — the sync manager treats null as "full initial sync".
 * - `after_id` is the second half of that cursor: the PK of the last row of
 *   the last applied page. See the doc on the field.
 * - `last_synced_at` is when the most recent successful page came back,
 *   used for diagnostics.
 * - `last_error` is the most recent error message; cleared on the next
 *   successful sync.
 * - `epoch` is the server-side generation (`sync_epoch`) whose full replay
 *   this device already finished. See the doc on the field.
 */
@Entity(tableName = "cobranza_sync_state")
data class CobranzaSyncStateEntity(
    @PrimaryKey val RESOURCE: String,
    val ZONA_CLIENTE_ID: Int,
    val CURSOR: String?,
    val LAST_SYNCED_AT: String,
    val LAST_ERROR: String?,
    /**
     * Generación (`sync_epoch`) del servidor que este dispositivo YA terminó
     * de replicar por completo. Cuando el servidor cambia lo que proyecta
     * (p.ej. las coordenadas del pago pasan a salir de otra tabla) las filas
     * ya guardadas no vuelven a bajar por el cursor incremental — su
     * `UPDATED_AT` no cambió. El servidor sube su generación y el cliente,
     * al ver una distinta a la guardada aquí, limpia el cursor y replica
     * desde cero. Sustituye a los marcadores hardcodeados por incidente
     * (`MIGRATION_*`), que exigían un APK nuevo cada vez.
     *
     * Nullable a propósito: NULL significa "esta instalación nunca terminó
     * de aplicar una generación" — distinto de "terminó la generación 0" —
     * y es lo que heredan las filas que ya existían antes de la migración
     * 27→28. Cualquier generación válida que llegue del servidor será
     * distinta de NULL, así que esos dispositivos hacen un replay (una vez)
     * y quedan alineados.
     */
    val EPOCH: Int? = null,
    /**
     * Segunda mitad del cursor: la PK (`IMPTE_DOCTO_CC_ID` en pagos,
     * `DOCTO_CC_ID` en ventas) de la última fila de la última página aplicada.
     * El servidor pagina por el par `(UPDATED_AT, PK)`, así que sin este valor
     * el cursor por sí solo no distingue entre las filas que comparten
     * `UPDATED_AT`: retomar con `after_id = 0` reprocesa ese grupo empatado
     * desde el principio.
     *
     * Se persiste junto con [CURSOR], en la misma escritura, porque los dos
     * son un solo cursor partido en dos columnas. Invariante: donde uno se
     * escribe, el otro también; y todo camino que deje [CURSOR] en NULL debe
     * dejar este campo en 0 (hoy todos esos caminos borran la fila entera con
     * `CobranzaSyncStateDao.clear`, que lo cumple por construcción).
     *
     * NOT NULL con default 0: 0 es "desde el inicio del grupo", que es
     * exactamente el valor correcto tanto para una instalación nueva como
     * para las filas que heredan la migración 28→29.
     */
    @ColumnInfo(defaultValue = "0")
    val AFTER_ID: Int = 0
)
