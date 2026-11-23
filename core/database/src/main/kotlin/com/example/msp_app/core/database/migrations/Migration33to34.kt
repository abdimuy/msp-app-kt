package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v33 → v34: las cuatro tablas de la captura de garantías v2. Migración
 * **aditiva**: crea tablas e índices nuevos y no toca ninguna existente.
 *
 * ## Por qué es 33→34 y no 39→40
 *
 * El brief de la tarea 11 preveía que `main` llegara a la versión 39 antes de
 * esta rama, así que pedía `Migration39to40`. Al abrir la rama, `main` seguía
 * en la 33 (la última migración es `MIGRATION_32_33`, el buscador de clientes).
 * La regla que manda es la otra: **la versión es la de `AppDatabase.kt` en la
 * base más uno**, y una versión que ya está en `main` no se reutiliza. Por eso
 * esta migración es la 33→34; queda declarado en el reporte y en el PR.
 *
 * ## Qué crea
 *
 * - `garantia_folios_seguidos`: los folios que este teléfono abrió o tocó.
 * - `garantia_comandos_pendientes`: la cola de comandos con su clave, su
 *   cuerpo serializado y su hora congelada (D3).
 * - `garantia_fotos_pendientes`: las fotos, cada una con su propia clave.
 * - `garantia_catalogos_cache`: la copia del catálogo de acciones.
 *
 * Los `CREATE` están escritos contra el `34.json` que exporta Room
 * (`:core:database:kspDebugKotlin`), no a mano. `Migration33to34Test` valida el
 * resultado con `runMigrationsAndValidate`, así que un desajuste entre esta
 * migración y las entidades se pone rojo antes de llegar a un teléfono.
 *
 * Nada de `DROP`, `ALTER COLUMN`, cambios de llave ni
 * `fallbackToDestructiveMigration`: las filas que el cobrador ya tiene en el
 * teléfono no se tocan.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        crearFoliosSeguidos(db)
        crearComandosPendientes(db)
        crearFotosPendientes(db)
        crearCatalogosCache(db)
    }

    /** Los folios que este teléfono abrió o tocó; la bandeja de "mis folios". */
    private fun crearFoliosSeguidos(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `garantia_folios_seguidos` (" +
                "`ID` TEXT NOT NULL, " +
                "`FOLIO_ID` TEXT, " +
                "`ORIGEN` TEXT NOT NULL, " +
                "`CLIENTE_ID` INTEGER, " +
                "`VENTA_ID` INTEGER, " +
                "`ESTADO_LOCAL` TEXT NOT NULL, " +
                "`ABIERTO_EN` TEXT NOT NULL, " +
                "`ACTUALIZADO_EN` TEXT NOT NULL, " +
                "`ULTIMO_ERROR` TEXT, " +
                "PRIMARY KEY(`ID`))"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_garantia_folios_seguidos_FOLIO_ID` " +
                "ON `garantia_folios_seguidos` (`FOLIO_ID`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_garantia_folios_seguidos_ESTADO_LOCAL` " +
                "ON `garantia_folios_seguidos` (`ESTADO_LOCAL`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_garantia_folios_seguidos_ABIERTO_EN` " +
                "ON `garantia_folios_seguidos` (`ABIERTO_EN`)"
        )
    }

    /**
     * La cola de comandos. `CLAVE` es única porque es la `Idempotency-Key` que
     * el servidor exige estable; `FOLIO_REF`/`ARTICULO_REF`/`EVENTO_REF` llevan
     * índice porque el trabajador propaga ids filtrando por ellas.
     */
    private fun crearComandosPendientes(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `garantia_comandos_pendientes` (" +
                "`ID` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`CLAVE` TEXT NOT NULL, " +
                "`OPERACION` TEXT NOT NULL, " +
                "`METODO` TEXT NOT NULL, " +
                "`RUTA` TEXT NOT NULL, " +
                "`CUERPO` TEXT, " +
                "`CONTENT_TYPE` TEXT, " +
                "`DEVICE_CREATED_AT` TEXT NOT NULL, " +
                "`ESTADO` TEXT NOT NULL, " +
                "`ULTIMO_ERROR` TEXT, " +
                "`FOLIO_REF` TEXT, " +
                "`FOLIO_ID` TEXT, " +
                "`ARTICULO_REF` TEXT, " +
                "`ARTICULO_ID` TEXT, " +
                "`EVENTO_REF` TEXT, " +
                "`EVENTO_ID` TEXT, " +
                "`INTENTOS` INTEGER NOT NULL, " +
                "`CREADO_EN` TEXT NOT NULL, " +
                "`ACTUALIZADO_EN` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_garantia_comandos_pendientes_CLAVE` " +
                "ON `garantia_comandos_pendientes` (`CLAVE`)"
        )
        listOf("ESTADO", "FOLIO_REF", "FOLIO_ID", "ARTICULO_REF", "EVENTO_REF").forEach { columna ->
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_garantia_comandos_pendientes_$columna` " +
                    "ON `garantia_comandos_pendientes` (`$columna`)"
            )
        }
    }

    /** Las fotos: la clave es la de la imagen, y esperan a tener su evento. */
    private fun crearFotosPendientes(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `garantia_fotos_pendientes` (" +
                "`CLAVE` TEXT NOT NULL, " +
                "`COMANDO_ID` INTEGER, " +
                "`FOLIO_REF` TEXT, " +
                "`FOLIO_ID` TEXT, " +
                "`EVENTO_REF` TEXT, " +
                "`EVENTO_ID` TEXT, " +
                "`RUTA_ARCHIVO` TEXT NOT NULL, " +
                "`MIME` TEXT NOT NULL, " +
                "`ESTADO` TEXT NOT NULL, " +
                "`INTENTOS` INTEGER NOT NULL, " +
                "`ULTIMO_ERROR` TEXT, " +
                "`CREADO_EN` TEXT NOT NULL, " +
                "`ACTUALIZADO_EN` TEXT NOT NULL, " +
                "`SUBIDA_EN` TEXT, " +
                "PRIMARY KEY(`CLAVE`))"
        )
        listOf("ESTADO", "FOLIO_REF", "FOLIO_ID", "EVENTO_REF", "EVENTO_ID", "COMANDO_ID")
            .forEach { columna ->
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_garantia_fotos_pendientes_$columna` " +
                        "ON `garantia_fotos_pendientes` (`$columna`)"
                )
            }
    }

    /** El catálogo es un documento único: una sola fila, sin índices extra. */
    private fun crearCatalogosCache(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `garantia_catalogos_cache` (" +
                "`ID` INTEGER NOT NULL, " +
                "`CUERPO` TEXT NOT NULL, " +
                "`ACTUALIZADO_EN` TEXT NOT NULL, " +
                "PRIMARY KEY(`ID`))"
        )
    }
}
