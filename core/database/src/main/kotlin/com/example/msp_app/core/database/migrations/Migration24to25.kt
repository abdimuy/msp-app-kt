package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds SERVER_UUID column to local_sale_products, local_sale_combos, and
 * sale_image so the worker can persist stable UUIDs on first upload attempt
 * and reuse them on retries for idempotency.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_sale_products ADD COLUMN SERVER_UUID TEXT")
        db.execSQL("ALTER TABLE local_sale_combos ADD COLUMN SERVER_UUID TEXT")
        db.execSQL("ALTER TABLE sale_image ADD COLUMN SERVER_UUID TEXT")
    }
}
