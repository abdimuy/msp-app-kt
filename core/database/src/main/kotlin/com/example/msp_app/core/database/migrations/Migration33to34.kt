package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Tabla nueva `envio_diagnostico` (E-APP-063): el motivo del último fallo de
 * envío por pago y por visita, solo para diagnóstico.
 *
 * Únicamente `CREATE TABLE`: no toca, no reescribe y no lee ninguna fila de
 * `Payment`, `Visit` ni de ninguna otra tabla. Lo prueba `Migration33to34Test`
 * comparando byte por byte las filas de `Payment` y `Visit` antes y después.
 *
 * El SQL es el que Room genera para [com.example.msp_app.core.database.entities.EnvioDiagnosticoEntity]
 * (`34.json`); `runMigrationsAndValidate` falla si divergen.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `envio_diagnostico` (" +
                "`TIPO` TEXT NOT NULL, " +
                "`FILA_ID` TEXT NOT NULL, " +
                "`ULTIMO_ERROR_CODIGO` TEXT, " +
                "`ULTIMO_ERROR_MENSAJE` TEXT, " +
                "`ULTIMO_ERROR_HTTP` INTEGER, " +
                "`INTENTOS` INTEGER NOT NULL, " +
                "`ULTIMO_INTENTO_AT` TEXT, " +
                "`ULTIMO_EXITO_AT` TEXT, " +
                "`REPORTADO_CODIGO` TEXT, " +
                "PRIMARY KEY(`TIPO`, `FILA_ID`))"
        )
    }
}
