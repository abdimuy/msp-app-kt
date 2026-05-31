package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the incremental-sync cursor for each cobranza resource the app
 * polls from the v2 Go backend. One row per resource (`ventas`, `pagos`).
 *
 * - `cursor` is the RFC3339 `max_updated_at` value the backend returned on
 *   the last successful page. Null when the resource has never been
 *   synced — the sync manager treats null as "full initial sync".
 * - `last_synced_at` is when the most recent successful page came back,
 *   used for diagnostics.
 * - `last_error` is the most recent error message; cleared on the next
 *   successful sync.
 */
@Entity(tableName = "cobranza_sync_state")
data class CobranzaSyncStateEntity(
    @PrimaryKey val RESOURCE: String,
    val ZONA_CLIENTE_ID: Int,
    val CURSOR: String?,
    val LAST_SYNCED_AT: String,
    val LAST_ERROR: String?
)
