package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_35_36
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-35-36-test.db"
private const val OLD_VERSION = 35
private const val NEW_VERSION = 36
private const val ULTIMA_VERSION = 39

/**
 * E-APP-080 (spec visitas §5.4.8, §5.2.5): la 35→36 crea `visita_zona` y
 * `visitas_sync_state`, vacías, y agrega a `Visit` `COMPROMISO_CONFIRMADO_EN` y
 * `COMPROMISO_RESGUARDADO_EN`, nulas. Ninguna fila se reescribe: cada columna
 * preexistente de cada tabla de v35 queda byte-idéntica (`rowid`, `typeof`,
 * `quote`) y el SQL de toda tabla e índice distinto de `Visit` no cambia.
 *
 * La siembra sale del propio esquema v35 (`PRAGMA table_info`), como en
 * [MigracionDesde27y28HastaV36Test]: toda tabla recibe filas y toda columna un
 * valor, así que una tabla nueva de una versión futura no queda sin cubrir. A
 * eso se suman visitas con forma real (pendiente con promesa, entregada y
 * confirmada) para leer cómo salen las dos marcas nuevas.
 */
class Migration35to36Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `E-APP-080 v36 crea visita_zona y visitas_sync_state vacias y Visit estrena las dos marcas nulas`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { sembrarVisitas(it) }

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_35_36
        )
        val filas = migrada.query(
            "SELECT ID, GUARDADO_EN_MICROSIP, CONFIRMADA_EN, COMPROMISO_CONFIRMADO_EN IS NULL, " +
                "COMPROMISO_RESGUARDADO_EN IS NULL FROM Visit ORDER BY ID"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        listOf(
                            c.getString(0),
                            c.getInt(1),
                            c.getString(2),
                            c.getInt(3),
                            c.getInt(4)
                        )
                    )
                }
            }
        }
        val conteos = listOf("visita_zona", "visitas_sync_state").associateWith { t ->
            migrada.query("SELECT COUNT(*) FROM `$t`").use {
                it.moveToFirst()
                it.getInt(0)
            }
        }
        migrada.close()

        assertEquals(
            listOf(
                listOf(ENTREGADA, 1, "2026-10-16T18:00:01Z", 1, 1),
                listOf(PENDIENTE_CON_PROMESA, 0, null, 1, 1)
            ),
            filas
        )
        assertEquals(mapOf("visita_zona" to 0, "visitas_sync_state" to 0), conteos)
    }

    @Test
    fun `E-APP-080 ninguna fila preexistente cambia, solo Visit gana dos columnas y nacen dos tablas`() {
        val (columnasAntes, filasAntes, sqlAntes) =
            migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
                val tablas = tablasDeUsuario(db)
                tablas.sortedBy { conLlaveForanea(db, it) }.forEach { sembrar(db, it) }
                Triple(
                    tablas.associateWith { columnas(db, it) },
                    tablas.associateWith { volcado(db, it, columnas(db, it)) },
                    esquema(db)
                )
            }
        // Control positivo: el volcado ve filas en CADA tabla de v35 (21 en 35.json).
        assertEquals(TABLAS_V35, columnasAntes.size)
        assertTrue(
            "hay filas que comparar en cada tabla ${filasAntes.mapValues { it.value.size }}",
            filasAntes.values.all { it.size == FILAS }
        )

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_35_36
        )
        val filasDespues = columnasAntes.mapValues { (t, cols) -> volcado(migrada, t, cols) }
        val ganadas = columnasAntes
            .mapValues { (t, cols) -> columnas(migrada, t) - cols.toSet() }
            .filterValues { it.isNotEmpty() }
        val sqlDespues = esquema(migrada)
        val tablasDespues = tablasDeUsuario(migrada)
        migrada.close()

        assertEquals(filasAntes, filasDespues)
        assertEquals(
            "solo nacen visita_zona y visitas_sync_state",
            (columnasAntes.keys + listOf("visita_zona", "visitas_sync_state")).sorted(),
            tablasDespues
        )
        assertEquals(
            mapOf("Visit" to listOf("COMPROMISO_CONFIRMADO_EN", "COMPROMISO_RESGUARDADO_EN")),
            ganadas
        )
        assertEquals(
            "el SQL de toda tabla e índice previo distinto de Visit queda igual",
            sqlAntes.filterKeys { it != "Visit" },
            sqlDespues.filterKeys { it in sqlAntes.keys && it != "Visit" }
        )
    }

    /** Camino real de producción: `buildDatabase` debe tener registrada la 35→36. */
    @Test
    fun `MIGRATION_35_36 esta registrada en la configuracion real que usa produccion`() {
        val antes = migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            sembrarVisitas(db)
            volcado(db, "Visit", columnas(db, "Visit"))
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val abierta = AppDatabase.buildDatabase(context, context.getDatabasePath(MIGRATION_DB).path)
            .allowMainThreadQueries()
            .build()
        try {
            val db = abierta.openHelper.readableDatabase
            assertEquals(ULTIMA_VERSION, db.version)
            assertEquals(antes, volcado(db, "Visit", antes.first().keys.filter { it != "rowid" }))
            db.query(
                "SELECT COUNT(*) FROM Visit WHERE COMPROMISO_CONFIRMADO_EN IS NULL " +
                    "AND COMPROMISO_RESGUARDADO_EN IS NULL"
            ).use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }
        } finally {
            abierta.close()
        }
    }

    // ─── siembra ──────────────────────────────────────────────────────────────

    private fun sembrarVisitas(db: SupportSQLiteDatabase) {
        listOf(
            arrayOf<Any?>(
                PENDIENTE_CON_PROMESA, 11486, "Mendoza Torres, Ana", 2991009, "2026-10-15T18:00:00Z", 0,
                19.4326077, -99.133208, "No estaba, vuelvo el jueves", "PROMESA", 21552, 512345, 0,
                512345, "2026-10-17", 15000L, "2026-10-17", "10:30", null, null
            ),
            arrayOf<Any?>(
                ENTREGADA, 30144, "Domínguez Reyes, Efraín", 7, "2026-10-10T17:30:00Z", 157,
                19.043415, -98.198234, "Ñandú — acentos áéíóú", "CON_PAGO", 21, 700002, 1,
                null, null, null, null, null, "2026-10-16T18:00:01Z", null
            )
        ).forEach {
            db.execSQL(
                "INSERT INTO Visit (ID, CLIENTE_ID, COBRADOR, COBRADOR_ID, FECHA, FORMA_COBRO_ID, LAT, LNG, " +
                    "NOTA, TIPO_VISITA, ZONA_CLIENTE_ID, IMPTE_DOCTO_CC_ID, GUARDADO_EN_MICROSIP, " +
                    "PROMESA_VENTA_ID, PROMESA_FECHA, PROMESA_MONTO_CENTAVOS, CITA_FECHA, CITA_HORA, " +
                    "CONFIRMADA_EN, RESGUARDADA_EN) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                it
            )
        }
    }

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
        const val PENDIENTE_CON_PROMESA = "a1b2c3d4-0000-3000-8000-000000000001"
        const val ENTREGADA = "a1b2c3d4-0000-3000-8000-000000000000"
        const val FILAS = 4
        const val TABLAS_V35 = 21
    }
}
