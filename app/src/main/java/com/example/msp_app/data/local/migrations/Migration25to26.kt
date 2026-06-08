package com.example.msp_app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds upload-failure tracking columns to local_sale so the worker can
 * persist the FIRST meaningful server error and the UI can surface it.
 *
 * The reconcile-via-GET fix (commit 6caa024) closed the "server has the
 * venta, client doesn't know" case. This migration closes the inverse
 * case: the server rejected the venta with a real error and the client
 * needs to remember WHY so the vendedor sees it instead of an endless
 * "Pendiente" badge.
 *
 * Columns:
 *  - LAST_UPLOAD_HTTP_CODE    server HTTP status (e.g. 422, 500)
 *  - LAST_UPLOAD_ERROR_CODE   server error.code (e.g. "plazo_invalido")
 *  - LAST_UPLOAD_ERROR_MESSAGE  server error.message (Spanish, end-user)
 *  - LAST_UPLOAD_AT           epoch millis when this failure was recorded
 *  - LAST_UPLOAD_PERMANENT    1 if classified as permanent, 0 transient
 *  - IDEMPOTENCY_KEY          current Idempotency-Key (null → fall back
 *                             to LOCAL_SALE_ID; regenerated on edit so
 *                             a corrected body avoids cache-mismatch
 *                             against a prior cached 2xx).
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_sale ADD COLUMN LAST_UPLOAD_HTTP_CODE INTEGER")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN LAST_UPLOAD_ERROR_CODE TEXT")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN LAST_UPLOAD_ERROR_MESSAGE TEXT")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN LAST_UPLOAD_AT INTEGER")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN LAST_UPLOAD_PERMANENT INTEGER")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN IDEMPOTENCY_KEY TEXT")
    }
}
