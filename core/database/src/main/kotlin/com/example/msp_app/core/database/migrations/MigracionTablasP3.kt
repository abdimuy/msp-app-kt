package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room de visitas P3 (spec visitas §6.1, §6.5 y §6.6; plan visitas P3,
 * Tarea 10; E-APP-121): crea, vacías,
 *
 * - `tarea_zona`: las tareas de oficina bajadas de la zona (lectura);
 * - `tareas_sync_state`: su posición `(CURSOR, AFTER_ID)` y generación;
 * - `aviso_emitido`: la llave de idempotencia de "Visita asignada";
 * - `aviso_programado`: las alarmas que deben existir.
 *
 * Solo `CREATE TABLE`/`CREATE INDEX`: ninguna fila existente se toca. El SQL es
 * el que Room genera en el json del esquema. Lo prueba `MigracionDeP3Test`.
 *
 * ## Numeración (secuencia única del relevo 6: P2 = 36, Captura = 37, P3 = 38)
 *
 * El archivo y el `val` NO llevan números a propósito (un nombre por feature
 * evita el choque de archivos entre ramas). Esta migración es la 37→38 y se
 * registra DESPUÉS de `MIGRATION_36_37` (Captura). El SQL no cambia
 * ([crearTablasDeP3]). Una ruta 36→38 directa NO serviría: Room tomaría el
 * salto largo y saltaría la 37 de Captura.
 */
val MIGRATION_P3 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) = crearTablasDeP3(db)
}

/**
 * Las sentencias de P3, independientes del número de versión: no leen ni
 * alteran ninguna tabla previa, así que valen igual sobre la 36 de P2 que
 * sobre la 37 de Captura.
 */
internal fun crearTablasDeP3(db: SupportSQLiteDatabase) {
    SENTENCIAS_P3.forEach(db::execSQL)
}

private val SENTENCIAS_P3 = listOf(
    "CREATE TABLE IF NOT EXISTS `tarea_zona` (`ID` TEXT NOT NULL, `VENTA_ID` INTEGER NOT NULL, " +
        "`CLIENTE_ID` INTEGER NOT NULL, `ZONA_CLIENTE_ID` INTEGER NOT NULL, `FECHA` TEXT NOT NULL, " +
        "`HORA` TEXT, `NOTA` TEXT, `REEMPLAZA_A` TEXT, `CREATED_AT` TEXT NOT NULL, " +
        "`UPDATED_AT` TEXT NOT NULL, `CANCELADA_AT` TEXT, PRIMARY KEY(`ID`))",
    "CREATE INDEX IF NOT EXISTS `index_tarea_zona_ZONA_CLIENTE_ID` ON `tarea_zona` (`ZONA_CLIENTE_ID`)",
    "CREATE INDEX IF NOT EXISTS `index_tarea_zona_VENTA_ID` ON `tarea_zona` (`VENTA_ID`)",
    "CREATE TABLE IF NOT EXISTS `tareas_sync_state` (`ZONA` INTEGER NOT NULL, `CURSOR` TEXT, " +
        "`AFTER_ID` TEXT, `EPOCH` INTEGER, PRIMARY KEY(`ZONA`))",
    "CREATE TABLE IF NOT EXISTS `aviso_emitido` (`ID` TEXT NOT NULL, `EMITIDO_EN` TEXT NOT NULL, " +
        "PRIMARY KEY(`ID`))",
    "CREATE TABLE IF NOT EXISTS `aviso_programado` (`ID` TEXT NOT NULL, `INSTANTE` TEXT NOT NULL, " +
        "`TIPO` TEXT NOT NULL, `ORIGEN_ID` TEXT NOT NULL, PRIMARY KEY(`ID`))"
)
