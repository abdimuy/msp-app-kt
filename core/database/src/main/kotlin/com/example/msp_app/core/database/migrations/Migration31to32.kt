package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Una columna nueva en `cliente`: `NOMBRE_NORMALIZADO`, el `NOMBRE` sin acentos, en
 * minúsculas y con espacios colapsados (`normalizeForSearch`, `:app`
 * `core/utils/FuzzyClientSearch.kt`). Arregla el defecto de campo: "por un acento o un
 * espacio de más, el buscador de la Nueva Venta no encuentra al cliente".
 *
 * SQLite no sabe plegar acentos, así que la comparación insensible a acentos tiene que
 * hacerse contra un valor ya normalizado en Kotlin — de ahí la columna, no una expresión
 * en la consulta. `ClienteDao.searchByNormalizedWord` es quien la lee.
 *
 * `NOT NULL DEFAULT ''` porque `cliente` se reemplaza entera en cada sincronización
 * (`ClienteDao.replaceAll` = `deleteAll` + `insertAll`, ver `ClienteRepository`): las
 * filas preexistentes quedan con la columna vacía hasta el próximo sync (no se
 * encuentran por nombre entre tanto, pero tampoco rompen la tabla ni pierden el resto
 * de sus datos), y el sync periódico (`ClienteSyncWorker`, cada vez que hay ventana
 * laboral) la repuebla sola sin que el usuario tenga que intervenir.
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE cliente ADD COLUMN NOMBRE_NORMALIZADO TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cliente_NOMBRE_NORMALIZADO` " +
                "ON `cliente` (`NOMBRE_NORMALIZADO`)"
        )
    }
}
