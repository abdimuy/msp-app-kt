package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_P3
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Las dos versiones entre las que corre la migración de P3: 37→38 (la 37 es la
 * de Captura; ver `MigracionTablasP3.kt`).
 */
private const val VERSION_ANTERIOR = 37
private const val VERSION_P3 = 38
private val MIGRACION_P3 = MIGRATION_P3

/** Tablas de usuario de la versión anterior (las de 37.json). */
private const val TABLAS_ANTES = 24
private const val MIGRATION_DB = "migration-p3-test.db"
private val TABLAS_P3 =
    listOf("aviso_emitido", "aviso_programado", "tarea_zona", "tareas_sync_state")

/**
 * E-APP-121 (spec visitas §6.1, §6.5, §6.6; plan visitas P3, Tarea 10, paso 1):
 * la migración de P3 crea `tarea_zona`, `tareas_sync_state`, `aviso_emitido` y
 * `aviso_programado`, vacías, y no toca nada más: cada fila preexistente de
 * cada tabla queda byte-idéntica (`rowid`, `typeof`, `quote`) y el SQL de toda
 * tabla e índice previo no cambia.
 *
 * La siembra sale del propio esquema anterior (`PRAGMA table_info`), como en
 * `Migration35to36Test`: toda tabla recibe filas y toda columna un valor.
 */
class MigracionDeP3Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `E-APP-121 nacen las cuatro tablas de P3 vacias y validan contra el esquema de Room`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, VERSION_ANTERIOR).close()

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            VERSION_P3,
            true,
            MIGRACION_P3
        )
        val conteos = TABLAS_P3.associateWith { t ->
            migrada.query("SELECT COUNT(*) FROM `$t`").use {
                it.moveToFirst()
                it.getInt(0)
            }
        }
        migrada.close()

        assertEquals(TABLAS_P3.associateWith { 0 }, conteos)
    }

    @Test
    fun `E-APP-121 ninguna fila ni tabla previa cambia y solo nacen las cuatro de P3`() {
        val (columnasAntes, filasAntes, sqlAntes) =
            migrationTestHelper.createDatabase(MIGRATION_DB, VERSION_ANTERIOR).use { db ->
                val tablas = tablasDeUsuario(db)
                tablas.sortedBy { conLlaveForanea(db, it) }.forEach { sembrar(db, it) }
                Triple(
                    tablas.associateWith { columnas(db, it) },
                    tablas.associateWith { volcado(db, it, columnas(db, it)) },
                    esquema(db)
                )
            }
        // Control positivo: el volcado ve filas en CADA tabla previa.
        assertEquals(TABLAS_ANTES, columnasAntes.size)
        assertTrue(
            "hay filas que comparar en cada tabla ${filasAntes.mapValues { it.value.size }}",
            filasAntes.values.all { it.size == FILAS }
        )

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            VERSION_P3,
            true,
            MIGRACION_P3
        )
        val filasDespues = columnasAntes.mapValues { (t, cols) -> volcado(migrada, t, cols) }
        val columnasDespues = columnasAntes.mapValues { (t, _) -> columnas(migrada, t) }
        val sqlDespues = esquema(migrada)
        val tablasDespues = tablasDeUsuario(migrada)
        migrada.close()

        assertEquals(filasAntes, filasDespues)
        assertEquals("ninguna tabla previa gana columnas", columnasAntes, columnasDespues)
        assertEquals((columnasAntes.keys + TABLAS_P3).sorted(), tablasDespues)
        assertEquals(
            "el SQL de toda tabla e índice previo queda igual",
            sqlAntes,
            sqlDespues.filterKeys { it in sqlAntes.keys }
        )
    }

    /** Camino real de producción: `buildDatabase` debe tener registrada la migración de P3. */
    @Test
    fun `E-APP-121 la migracion de P3 esta registrada en la configuracion real que usa produccion`() {
        val antes = migrationTestHelper.createDatabase(MIGRATION_DB, VERSION_ANTERIOR).use { db ->
            sembrar(db, "Visit")
            volcado(db, "Visit", columnas(db, "Visit"))
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val abierta = AppDatabase.buildDatabase(context, context.getDatabasePath(MIGRATION_DB).path)
            .allowMainThreadQueries()
            .build()
        try {
            val db = abierta.openHelper.readableDatabase
            assertEquals(VERSION_P3, db.version)
            assertEquals(antes, volcado(db, "Visit", antes.first().keys.filter { it != "rowid" }))
            assertTrue(tablasDeUsuario(db).containsAll(TABLAS_P3))
        } finally {
            abierta.close()
        }
    }

    // ─── siembra ──────────────────────────────────────────────────────────────

    /** Siembra por nombre y tipo; las anulables alternan con NULL. */
    private fun sembrar(db: SupportSQLiteDatabase, tabla: String) {
        val info = db.query("PRAGMA table_info(`$tabla`)").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Triple(
                            c.getString(c.getColumnIndexOrThrow("name")),
                            c.getString(c.getColumnIndexOrThrow("type")).uppercase(),
                            c.getInt(c.getColumnIndexOrThrow("notnull")) == 1
                        )
                    )
                }
            }
        }
        repeat(FILAS) { i ->
            val valores = info.map { (nombre, tipo, notNull) ->
                when {
                    nombre == "ID" && tipo == "TEXT" -> "$tabla-$i-7f3c2a10-5b1e-4c7a-9d2f"
                    !notNull && i % 2 == 1 -> null
                    nombre == "GUARDADO_EN_MICROSIP" -> i % 2
                    tipo.startsWith("INT") -> 2991009L + i
                    tipo == "REAL" -> 150.25 + i
                    else -> "López Núñez $nombre $i"
                }
            }
            db.execSQL(
                "INSERT INTO `$tabla` (${info.joinToString { "`${it.first}`" }}) " +
                    "VALUES (${info.joinToString { "?" }})",
                valores.toTypedArray()
            )
        }
    }

    private fun conLlaveForanea(db: SupportSQLiteDatabase, tabla: String): Boolean =
        db.query("PRAGMA foreign_key_list(`$tabla`)").use { it.count > 0 }

    // ─── volcado byte a byte ───────────────────────────────────────────────────

    private fun tablasDeUsuario(db: SupportSQLiteDatabase): List<String> = db.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name"
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun esquema(db: SupportSQLiteDatabase): Map<String, String?> = db.query(
        "SELECT name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name != 'room_master_table'"
    ).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) } }

    private fun columnas(db: SupportSQLiteDatabase, tabla: String): List<String> =
        db.query("PRAGMA table_info(`$tabla`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    /** Cada fila como columna → `typeof:quote`, más `rowid`, en orden de `rowid`. */
    private fun volcado(
        db: SupportSQLiteDatabase,
        tabla: String,
        cols: List<String>
    ): List<Map<String, String>> {
        val datos = cols.filter { it != "rowid" }
        val select = (listOf("rowid") + datos.map { "typeof(`$it`) || ':' || quote(`$it`)" })
            .joinToString()
        return db.query("SELECT $select FROM `$tabla` ORDER BY rowid").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        buildMap {
                            put("rowid", c.getString(0))
                            datos.forEachIndexed { i, col -> put(col, c.getString(i + 1)) }
                        }
                    )
                }
            }
        }
    }

    private companion object {
        const val FILAS = 4
    }
}
