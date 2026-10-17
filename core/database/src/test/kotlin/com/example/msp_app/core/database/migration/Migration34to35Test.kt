package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_34_35
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-34-35-test.db"
private const val OLD_VERSION = 34
private const val NEW_VERSION = 35

/**
 * Spec visitas §4.2-7: la 34→35 agrega `Visit.CONFIRMADA_EN` y
 * `Visit.RESGUARDADA_EN`, nulas, y no reescribe ninguna fila.
 *
 * Lo que importa (Review Focus 2 del plan P1): una visita legada ya soltada
 * (`GUARDADO_EN_MICROSIP = 1`) sale de la migración con `CONFIRMADA_EN` nulo —
 * o sea, **sin prueba del servidor** — para que la reconciliación inversa la
 * confirme o la devuelva a pendiente. Cada columna preexistente de cada tabla
 * queda byte-idéntica (`rowid`, `typeof`, `quote`).
 */
class Migration34to35Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `v35 agrega CONFIRMADA_EN y RESGUARDADA_EN nulas en pendientes y legadas entregadas`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { sembrar(it) }

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_34_35
        )
        val filas = migrada.query(
            "SELECT ID, GUARDADO_EN_MICROSIP, CONFIRMADA_EN, RESGUARDADA_EN FROM Visit ORDER BY ID"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(listOf(c.getString(0), c.getInt(1), c.isNull(2), c.isNull(3)))
                }
            }
        }
        migrada.close()

        assertEquals(
            "pendientes y legadas entregadas conservan su estado y estrenan las dos marcas en NULL",
            listOf(
                listOf(PENDIENTE_CON_FOTO, 0, true, true),
                listOf(PENDIENTE, 0, true, true),
                listOf(LEGADA_ENTREGADA, 1, true, true),
                listOf(LEGADA_ENTREGADA_CON_PROMESA, 1, true, true)
            ),
            filas
        )
    }

    @Test
    fun `ninguna fila preexistente cambia y solo Visit gana dos columnas`() {
        val antes = migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            sembrar(db)
            val tablas = tablasDeUsuario(db)
            Triple(
                tablas.associateWith { columnas(db, it) },
                tablas.associateWith { volcado(db, it, columnas(db, it)) },
                esquema(db)
            )
        }
        val (columnasAntes, filasAntes, sqlAntes) = antes
        // Control positivo: el volcado sí ve lo sembrado.
        assertEquals(4, filasAntes.getValue("Visit").size)
        assertEquals(2, filasAntes.getValue("Payment").size)
        assertEquals(1, filasAntes.getValue("visita_imagenes").size)

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_34_35
        )
        val filasDespues = columnasAntes.mapValues { (tabla, cols) ->
            volcado(
                migrada,
                tabla,
                cols
            )
        }
        val ganadas = columnasAntes
            .mapValues { (tabla, cols) -> columnas(migrada, tabla) - cols.toSet() }
            .filterValues { it.isNotEmpty() }
        val sqlDespues = esquema(migrada)
        val tablasDespues = tablasDeUsuario(migrada)
        migrada.close()

        assertEquals(filasAntes, filasDespues)
        assertEquals("ninguna tabla nueva", columnasAntes.keys.toList(), tablasDespues)
        assertEquals(mapOf("Visit" to listOf("CONFIRMADA_EN", "RESGUARDADA_EN")), ganadas)
        assertEquals(
            "el SQL de toda tabla e índice distinto de Visit queda igual",
            sqlAntes.filterKeys { it != "Visit" },
            sqlDespues.filterKeys { it != "Visit" }
        )
    }

    /** Camino real de producción: `buildDatabase` debe tener registrada la 34→35. */
    @Test
    fun `MIGRATION_34_35 esta registrada en la configuracion real que usa produccion`() {
        val antes = migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            sembrar(db)
            volcado(db, "Visit", columnas(db, "Visit"))
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val abierta = AppDatabase.buildDatabase(context, context.getDatabasePath(MIGRATION_DB).path)
            .allowMainThreadQueries()
            .build()
        try {
            val db = abierta.openHelper.readableDatabase
            assertEquals(NEW_VERSION, db.version)
            assertEquals(antes, volcado(db, "Visit", antes.first().keys.toList()))
            db.query(
                "SELECT COUNT(*) FROM Visit WHERE CONFIRMADA_EN IS NULL AND RESGUARDADA_EN IS NULL"
            )
                .use { c ->
                    c.moveToFirst()
                    assertEquals(4, c.getInt(0))
                }
        } finally {
            abierta.close()
        }
    }

    // ─── siembra con forma real ────────────────────────────────────────────────

    private fun sembrar(db: SupportSQLiteDatabase) {
        val visitas = listOf(
            arrayOf<Any?>(
                PENDIENTE, 11486, "Mendoza Torres, Ana", 2991009, "2026-10-15T18:00:00Z", 0,
                19.4326077, -99.133208, "No estaba, vuelvo el jueves", "SIN_PAGO", 21552, 512345, 0,
                512345, "2026-10-17", 15000L, "2026-10-17", "10:30"
            ),
            arrayOf<Any?>(
                PENDIENTE_CON_FOTO, 22001, "Ramírez Ortiz, Fernando", 2991010,
                "2026-10-16T11:05:12.345Z", 0, 0.0, 0.0, null, "PIDE_TIEMPO", 21553, 600001, 0,
                null, null, null, null, null
            ),
            // Legada: soltada por el envío de hoy sin prueba del servidor.
            arrayOf<Any?>(
                LEGADA_ENTREGADA, 11486, "Mendoza Torres, Ana", 2991009, "2026-10-13T08:00:00Z",
                87327, 19.4, -99.1, "Ñandú — acentos áéíóú", "CON_PAGO", 21552, 512345, 1,
                null, null, null, null, null
            ),
            arrayOf<Any?>(
                LEGADA_ENTREGADA_CON_PROMESA, 30144, "Domínguez Reyes, Efraín", 7,
                "2026-10-10T17:30:00Z", 157, 19.043415, -98.198234, null, "PROMESA", 21, 700002, 1,
                700002, "2026-10-24", 25050L, null, null
            )
        )
        visitas.forEach {
            db.execSQL(
                "INSERT INTO Visit (ID, CLIENTE_ID, COBRADOR, COBRADOR_ID, FECHA, FORMA_COBRO_ID, LAT, LNG, " +
                    "NOTA, TIPO_VISITA, ZONA_CLIENTE_ID, IMPTE_DOCTO_CC_ID, GUARDADO_EN_MICROSIP, " +
                    "PROMESA_VENTA_ID, PROMESA_FECHA, PROMESA_MONTO_CENTAVOS, CITA_FECHA, CITA_HORA) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                it
            )
        }
        db.execSQL(
            "INSERT INTO visita_imagenes (ID, VISITA_ID, URI, MIME, DESCRIPCION, ORDEN, CREADA_EN, SUBIDA_EN) " +
                "VALUES ('img-v-1', '$PENDIENTE_CON_FOTO', '/data/fachada.jpg', 'image/jpeg', 'Fachada', 0, " +
                "'2026-10-16T11:05:13Z', NULL)"
        )
        listOf(
            arrayOf<Any?>(
                "7f3c2a10-5b1e-4c7a-9d2f-1a2b3c4d5e6f", "Mendoza Torres, Ana", 512345, -1,
                "2026-10-15T17:42:08.123Z", 0, 150.0, 19.4326077, -99.133208, 11486, 2991009, 157, 21552,
                "López García, Minerva", null
            ),
            arrayOf<Any?>(
                "0b9d8e7f-6a5b-4c3d-8e2f-9a8b7c6d5e4f", "Mendoza Torres, Ana", 512345, 8800123,
                "2026-10-14T15:00:00Z", 1, 200.5, 0.0, 0.0, 11486, 2991009, 158, 21552,
                "López García, Minerva", null
            )
        ).forEach {
            db.execSQL(
                "INSERT INTO Payment (ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO, " +
                    "GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID, COBRADOR_ID, FORMA_COBRO_ID, " +
                    "ZONA_CLIENTE_ID, NOMBRE_CLIENTE, PAGO_RECIBIDO_ID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                it
            )
        }
    }

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
        const val PENDIENTE = "a1b2c3d4-0000-3000-8000-000000000001"
        const val PENDIENTE_CON_FOTO = "a1b2c3d4-0000-3000-8000-000000000000"
        const val LEGADA_ENTREGADA = "a1b2c3d4-0000-3000-8000-000000000002"
        const val LEGADA_ENTREGADA_CON_PROMESA = "a1b2c3d4-0000-3000-8000-000000000003"
    }
}
