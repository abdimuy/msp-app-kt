package com.example.msp_app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the cobranza_sync_state table used by CobranzaSyncManager to persist
 * per-resource cursors for the incremental v2 sync.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cobranza_sync_state` (
                `RESOURCE` TEXT NOT NULL,
                `ZONA_CLIENTE_ID` INTEGER NOT NULL,
                `CURSOR` TEXT,
                `LAST_SYNCED_AT` TEXT NOT NULL,
                `LAST_ERROR` TEXT,
                PRIMARY KEY(`RESOURCE`)
            )
            """.trimIndent()
        )
    }
}
