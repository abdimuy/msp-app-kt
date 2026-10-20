package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 35 → 36 (E-APP-084): crea `capturas_en_curso`, el marcador de "captura nunca
 * se pierde". Sólo crea; no toca ninguna fila existente.
 *
 * Aviso de integración: `feat/visitas-p2-t17` también declara una 35→36
 * (`VisitaZonaEntity`, `VisitasSyncStateEntity`). La que se integre segunda
 * debe renumerarse a 36→37.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `capturas_en_curso` (" +
                "`ID` TEXT NOT NULL, " +
                "`TIPO` TEXT NOT NULL, " +
                "`LLAVE` TEXT NOT NULL, " +
                "`USUARIO` TEXT NOT NULL, " +
                "`ESTADO` TEXT NOT NULL, " +
                "`RESUMEN` TEXT NOT NULL, " +
                "`CREADA_EN` TEXT NOT NULL, " +
                "`ACTUALIZADA_EN` TEXT NOT NULL, " +
                "`DISCREPANCIA` TEXT, " +
                "`DISCREPANCIA_EN` TEXT, " +
                "PRIMARY KEY(`ID`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_capturas_en_curso_tipo_llave_estado` " +
                "ON `capturas_en_curso` (`TIPO`, `LLAVE`, `ESTADO`)"
        )
    }
}
