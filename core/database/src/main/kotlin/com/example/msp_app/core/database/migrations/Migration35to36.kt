package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 35 → 36: **reservada para visitas P2** (secuencia única de versiones Room,
 * decidida por el coordinador el 2026-10-26: P2 = 36, Captura = 37, P3 = 38).
 *
 * En esta rama no hace nada: la 36 de verdad (`visita_zona`,
 * `visitas_sync_state` y las dos columnas de compromiso en `Visit`, E-APP-080)
 * vive en `feat/visitas-p2-telefono`. **Al integrar, esta versión se descarta y
 * se toma la de P2 tal cual**, con su `36.json`. La de Captura, 36 → 37
 * ([MIGRATION_36_37]), sólo crea su tabla con `IF NOT EXISTS`, así que se
 * compone encima de la 36 de P2 sin cambiar una línea.
 *
 * El `36.json` de esta rama es el de la 35 con la versión cambiada (mismo
 * esquema, mismo `identityHash`): describe lo que esta 35 → 36 vacía deja.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Vacía a propósito: ver el KDoc.
    }
}
