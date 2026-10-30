package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 38 → 39 (E-APP-145): crea `ubicacion_de_captura`, la ubicacion de cada
 * captura y el estado de su envio, y `plazo_de_envio`, el plazo de retencion del envio de cada
 * captura (K1 de la compuerta ANTES c2: tabla aparte, nunca dentro de `ubicacion_de_captura`).
 * Solo crea dos tablas; no toca ninguna tabla ni fila existente. Sin llave foranea: la fila vive por el UUID
 * de la captura y sobrevive a cualquier cambio de la tabla de la captura.
 *
 * `IF NOT EXISTS`: un telefono de desarrollo que ya la tenga pasa sin error.
 */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ubicacion_de_captura` (" +
                "`id` TEXT NOT NULL, " +
                "`tipo` TEXT NOT NULL, " +
                "`lat` REAL NOT NULL, " +
                "`lng` REAL NOT NULL, " +
                "`precision_m` REAL NOT NULL, " +
                "`fix_at_ms` INTEGER NOT NULL, " +
                "`origen` TEXT NOT NULL, " +
                "`estado_envio` TEXT NOT NULL, " +
                "`intentos` INTEGER NOT NULL, " +
                "`creada_en` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `plazo_de_envio` (" +
                "`id` TEXT NOT NULL, " +
                "`tipo` TEXT NOT NULL, " +
                "`guardado_en_epoch_ms` INTEGER NOT NULL, " +
                "`plazo_ms` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}
