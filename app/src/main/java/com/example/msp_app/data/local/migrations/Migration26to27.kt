package com.example.msp_app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Agrega PAGO_RECIBIDO_ID a Payment: el UUID original del pago capturado en el
 * dispositivo (MSP_PAGOS_RECIBIDOS.ID en el server). Persistirlo permite al
 * reconciliador colapsar el gemelo UUID local aunque mergePagos falle el
 * colapso de un solo tiro (carrera pull-vs-markDone o histórico). Nullable:
 * filas existentes quedan NULL.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Payment ADD COLUMN PAGO_RECIBIDO_ID TEXT")
    }
}
