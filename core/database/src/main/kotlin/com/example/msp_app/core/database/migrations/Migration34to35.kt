package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Spec visitas §4.2-7: `Visit` gana `CONFIRMADA_EN` y `RESGUARDADA_EN`, las dos
 * nulas. Solo `ALTER TABLE ADD COLUMN`: ninguna fila se reescribe, así que una
 * visita legada con `GUARDADO_EN_MICROSIP = 1` queda sin confirmar hasta que
 * la reconciliación inversa la pruebe. Lo prueba `Migration34to35Test`.
 */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Visit` ADD COLUMN `CONFIRMADA_EN` TEXT")
        db.execSQL("ALTER TABLE `Visit` ADD COLUMN `RESGUARDADA_EN` TEXT")
    }
}
