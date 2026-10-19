package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Spec visitas §5.4.8 y §5.2.5 (E-APP-080):
 *
 * - `visita_zona`: la agenda bajada de la zona, tabla de LECTURA aparte de la
 *   tabla de salida `Visit` (I8). Nace vacía.
 * - `visitas_sync_state`: la posición `(CURSOR, AFTER_ID)` y la generación por
 *   zona. Nace vacía: sin fila = sin posición, el primer sync baja todo.
 * - `Visit` gana `COMPROMISO_CONFIRMADO_EN` y `COMPROMISO_RESGUARDADO_EN`,
 *   nulas: ninguna visita existente tiene prueba de su compromiso, así que el
 *   relleno las reenvía todas.
 *
 * Solo `CREATE TABLE`/`CREATE INDEX` y `ALTER TABLE ADD COLUMN`: ninguna fila se
 * reescribe. El SQL es el que Room genera en `36.json`. Lo prueba
 * `Migration35to36Test`.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `visita_zona` (`ID` TEXT NOT NULL, `CLIENTE_ID` INTEGER NOT NULL, " +
                "`COBRADOR_ID` INTEGER, `COBRADOR` TEXT, `FECHA` TEXT NOT NULL, `TIPO_VISITA` TEXT NOT NULL, " +
                "`ZONA_CLIENTE_ID` INTEGER NOT NULL, `IMPTE_DOCTO_CC_ID` INTEGER, `NOTA` TEXT, " +
                "`PROMESA_VENTA_ID` INTEGER, `PROMESA_FECHA` TEXT, `PROMESA_MONTO_CENTAVOS` INTEGER, " +
                "`CITA_FECHA` TEXT, `CITA_HORA` TEXT, `UPDATED_AT` TEXT NOT NULL, PRIMARY KEY(`ID`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_visita_zona_ZONA_CLIENTE_ID` ON `visita_zona` (`ZONA_CLIENTE_ID`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_visita_zona_IMPTE_DOCTO_CC_ID` ON `visita_zona` (`IMPTE_DOCTO_CC_ID`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_visita_zona_CLIENTE_ID` ON `visita_zona` (`CLIENTE_ID`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `visitas_sync_state` (`ZONA` INTEGER NOT NULL, `CURSOR` TEXT, " +
                "`AFTER_ID` TEXT, `EPOCH` INTEGER, PRIMARY KEY(`ZONA`))"
        )
        db.execSQL("ALTER TABLE `Visit` ADD COLUMN `COMPROMISO_CONFIRMADO_EN` TEXT")
        db.execSQL("ALTER TABLE `Visit` ADD COLUMN `COMPROMISO_RESGUARDADO_EN` TEXT")
    }
}
