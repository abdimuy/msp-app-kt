package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Agrega EPOCH a `cobranza_sync_state`: la generación (`sync_epoch`) del
 * servidor que el dispositivo ya terminó de replicar. Habilita el resync por
 * generación del sync de cobranza — cuando el servidor cambia lo que proyecta,
 * sube su epoch y el cliente replica desde cero sin necesidad de un APK con un
 * marcador nuevo por incidente.
 *
 * Nullable y sin DEFAULT: las filas existentes quedan en NULL, que el manager
 * lee como "nunca aplicó una generación" y por tanto distinta de cualquier
 * epoch válido del servidor — el primer sync tras actualizar replica una vez y
 * se alinea. Solo se agrega una columna: cursores, zona y timestamps
 * preexistentes quedan intactos.
 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cobranza_sync_state ADD COLUMN EPOCH INTEGER")
    }
}
