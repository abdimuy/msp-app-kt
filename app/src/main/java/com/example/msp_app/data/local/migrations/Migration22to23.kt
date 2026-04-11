package com.example.msp_app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Recreates overdue_payments_view so its SQL in sqlite_master matches
 * what Room generates from the @DatabaseView annotation.
 * The previous migration (21→22) used trimIndent() which produced
 * whitespace that differed from Room's expected format, causing
 * schema validation failures on upgrade.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP VIEW IF EXISTS `overdue_payments_view`")
    }
}
