package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 38 → 39 (E-APP-145): crea `ubicacion_de_captura`, la ubicacion de cada
 * captura y el estado de su envio. Solo crea una tabla y un indice; no toca
 * ninguna tabla ni fila existente. Sin llave foranea: la fila vive por el UUID
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
    }
}
