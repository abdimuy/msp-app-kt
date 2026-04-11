package com.example.msp_app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.msp_app.data.local.entities.OVERDUE_PAYMENTS_VIEW_SQL

/**
 * Recreates overdue_payments_view so its SQL in sqlite_master matches
 * what Room generates from the @DatabaseView annotation.
 * Uses the shared constant [OVERDUE_PAYMENTS_VIEW_SQL] to guarantee
 * the migration SQL always matches the annotation.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP VIEW IF EXISTS `overdue_payments_view`")
        db.execSQL(
            "CREATE VIEW `overdue_payments_view` AS ${OVERDUE_PAYMENTS_VIEW_SQL.trim()}"
        )
    }
}
