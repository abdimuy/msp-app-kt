package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.msp_app.core.database.entities.OVERDUE_PAYMENTS_VIEW_SQL

/**
 * 36 → 37 (E-APP-084): crea `capturas_en_curso`, el marcador de "captura nunca
 * se pierde". Sólo crea; no toca ninguna fila existente.
 *
 * También recrea `overdue_payments_view` para que no cuente el abono -2
 * ("no se aplicó", E-APP-092); la vista no guarda filas, sólo cambia su SQL.
 *
 * Era la 35 → 36 de Captura; se renumeró por la secuencia única de versiones
 * Room (coordinador, 2026-10-26: P2 = 36, Captura = 37, P3 = 38).
 *
 * **Cómo se compone con la 36 de P2** (que no está en esta rama):
 * - P2 sólo crea `visita_zona` y `visitas_sync_state` y agrega dos columnas a
 *   `Visit`; ninguna toca `capturas_en_curso`. Esta migración no lee ni toca
 *   ninguna de esas tablas, así que aplica igual sobre la 36 de P2 que sobre la
 *   36 vacía de esta rama.
 * - `IF NOT EXISTS` en la tabla y el índice: un teléfono de desarrollo que ya
 *   tenía `capturas_en_curso` por la 36 vieja de Captura (antes de renumerar)
 *   pasa por aquí sin error y Room valida contra el `37.json`.
 * - Al integrar, el `37.json` se regenera con la compilación (trae las tablas
 *   de P2 más la de Captura); el SQL de aquí es el que Room genera para esta
 *   tabla y no cambia.
 */
val MIGRATION_36_37 = object : Migration(36, 37) {
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
        db.execSQL("DROP VIEW IF EXISTS `overdue_payments_view`")
        db.execSQL("CREATE VIEW `overdue_payments_view` AS ${OVERDUE_PAYMENTS_VIEW_SQL.trim()}")
    }
}
