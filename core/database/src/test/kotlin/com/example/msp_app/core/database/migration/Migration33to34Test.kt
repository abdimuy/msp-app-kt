package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_33_34
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-33-34-test.db"
private const val OLD_VERSION = 33
private const val NEW_VERSION = 34
private const val ULTIMA_VERSION = 36

/**
 * E-APP-063: la 33→34 solo crea `envio_diagnostico`. Lo que se prueba aquí, y
 * que importa para el dinero: **todas** las filas de **todas** las tablas que
 * ya existían —`Payment` y `Visit` incluidas, con capturas pendientes, ya
 * soltadas, condonaciones y gemelos UUID— quedan **byte-idénticas**: mismo
 * valor, mismo tipo de almacenamiento de SQLite (`typeof`) y misma
 * representación literal (`quote`), y mismo `rowid`.
 */
class Migration33to34Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `E-APP-063 la migracion deja byte-identicas las filas de Payment y Visit`() {
        val antes = migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            sembrar(db)
            volcado(db, listOf("Payment", "Visit", "pago_imagenes", "visita_imagenes"))
        }
        // Control positivo: el volcado SÍ ve las filas sembradas (un volcado vacío
        // "idéntico" no probaría nada).
        assertEquals(4, antes.getValue("Payment").size)
        assertEquals(3, antes.getValue("Visit").size)

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_33_34
        )
        val despues = volcado(migrada, antes.keys.toList())
        migrada.close()

        assertEquals(antes, despues)
    }

    @Test
    fun `E-APP-063 ninguna tabla preexistente cambia de contenido ni de esquema`() {
        val (tablasAntes, sqlAntes) = migrationTestHelper.createDatabase(
            MIGRATION_DB,
            OLD_VERSION
        ).use { db ->
            sembrar(db)
            val tablas = tablasDeUsuario(db)
            volcado(db, tablas) to esquema(db)
        }
        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_33_34
        )
        val tablasDespues = volcado(migrada, tablasAntes.keys.toList())
        val sqlDespues = esquema(migrada)
        val nuevas = tablasDeUsuario(migrada) - tablasAntes.keys
        migrada.close()

        assertEquals(tablasAntes, tablasDespues)
        assertEquals("solo se agrega envio_diagnostico", setOf("envio_diagnostico"), nuevas.toSet())
        assertEquals(
            "el SQL de toda tabla/índice preexistente queda igual",
            sqlAntes,
            sqlDespues.filterKeys { it in sqlAntes.keys }
        )
    }

    @Test
    fun `envio_diagnostico nace vacia`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { sembrar(it) }
        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_33_34
        )
        migrada.query("SELECT COUNT(*) FROM envio_diagnostico").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        migrada.close()
    }

    /**
     * Camino real de producción: `buildDatabase` debe tener registrada la 33→34.
     * La cadena sigue hasta la última versión (v35 y v36 agregan columnas a
     * `Visit`), así que se comparan las columnas que la fila ya tenía en v33.
     */
    @Test
    fun `MIGRATION_33_34 esta registrada en la configuracion real que usa produccion`() {
        val (antes, columnasDeOrigen) = migrationTestHelper.createDatabase(
            MIGRATION_DB,
            OLD_VERSION
        )
            .use { db ->
                sembrar(db)
                val tablas = listOf("Payment", "Visit")
                volcado(db, tablas) to tablas.associateWith { columnas(db, it) }
            }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val abierta = AppDatabase.buildDatabase(context, context.getDatabasePath(MIGRATION_DB).path)
            .allowMainThreadQueries()
            .build()
        try {
            val db = abierta.openHelper.readableDatabase
            assertEquals(ULTIMA_VERSION, db.version)
            assertEquals(
                antes,
                volcado(db, listOf("Payment", "Visit")) { columnasDeOrigen.getValue(it) }
            )
        } finally {
            abierta.close()
        }
    }

    // ─── siembra con forma real ────────────────────────────────────────────────

    private fun sembrar(db: SupportSQLiteDatabase) {
        sembrarPagos(db)
        sembrarVisitas(db)
        sembrarComprobantes(db)
    }

    private fun sembrarPagos(db: SupportSQLiteDatabase) {
        val pagos = listOf(
            // Captura pendiente con UUID, sin docto todavía, coordenadas reales.
            arrayOf<Any?>(
                "7f3c2a10-5b1e-4c7a-9d2f-1a2b3c4d5e6f", "Mendoza Torres, Ana", 512345, -1,
                "2026-10-15T17:42:08.123Z", 0, 150.0, 19.4326077, -99.133208, 11486, 2991009, 157, 21552,
                "López García, Minerva", null
            ),
            // Soltada (GUARDADO=1) con docto anotado.
            arrayOf<Any?>(
                "0b9d8e7f-6a5b-4c3d-8e2f-9a8b7c6d5e4f", "Mendoza Torres, Ana", 512345, 8800123,
                "2026-10-14T15:00:00Z", 1, 200.5, 0.0, 0.0, 11486, 2991009, 158, 21552,
                "López García, Minerva", null
            ),
            // Condonación pendiente (137026 → concepto 27969).
            arrayOf<Any?>(
                "c0nd0n4c-1on0-4000-8000-000000000001", "Ramírez Ortiz, Fernando", 600001, -1,
                "2026-10-16T09:01:02Z", 0, 1234.56, null, null, 22001, 2991010, 137026, 21553,
                "Pérez Núñez, José María", null
            ),
            // Fila numérica del servidor que nombra a su gemelo UUID.
            arrayOf<Any?>(
                "99887766", "Mendoza Torres, Ana", 512345, 8800124, "2026-10-13T12:00:00Z", 1, 100.0,
                19.4, -99.1, 11486, 2991009, 52569, 21552, "López García, Minerva",
                "7f3c2a10-5b1e-4c7a-9d2f-1a2b3c4d5e6f"
            )
        )
        pagos.forEach {
            db.execSQL(
                "INSERT INTO Payment (ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO, " +
                    "GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID, COBRADOR_ID, FORMA_COBRO_ID, " +
                    "ZONA_CLIENTE_ID, NOMBRE_CLIENTE, PAGO_RECIBIDO_ID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                it
            )
        }
    }

    private fun sembrarVisitas(db: SupportSQLiteDatabase) {
        val visitas = listOf(
            arrayOf<Any?>(
                "a1b2c3d4-0000-3000-8000-000000000001", 11486, "Mendoza Torres, Ana", 2991009,
                "2026-10-15T18:00:00Z", 0, 19.43, -99.13, "No estaba, vuelvo el jueves", "SIN_PAGO",
                21552, 512345, 0, 512345, "2026-10-17", 15000, "2026-10-17", "10:30"
            ),
            arrayOf<Any?>(
                "a1b2c3d4-0000-3000-8000-000000000002", 22001, "Ramírez Ortiz, Fernando", 2991010,
                "2026-10-14T11:00:00Z", 0, 0.0, 0.0, null, "PIDE_TIEMPO", 21553, 600001, 1,
                null, null, null, null, null
            ),
            arrayOf<Any?>(
                "a1b2c3d4-0000-3000-8000-000000000003", 11486, "Mendoza Torres, Ana", 2991009,
                "2026-10-13T08:00:00Z", 87327, 19.4, -99.1, "Ñandú — acentos áéíóú", "CON_PAGO",
                21552, 512345, 0, null, null, null, null, null
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
    }

    private fun sembrarComprobantes(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO pago_imagenes (ID, PAGO_ID, URI, MIME, DESCRIPCION, ORDEN, CREADA_EN, SUBIDA_EN) " +
                "VALUES ('img-p-1', '7f3c2a10-5b1e-4c7a-9d2f-1a2b3c4d5e6f', '/data/comprobante.jpg', " +
                "'image/jpeg', NULL, 0, '2026-10-15T17:42:09Z', NULL)"
        )
        db.execSQL(
            "INSERT INTO visita_imagenes (ID, VISITA_ID, URI, MIME, DESCRIPCION, ORDEN, CREADA_EN, SUBIDA_EN) " +
                "VALUES ('img-v-1', 'a1b2c3d4-0000-3000-8000-000000000001', '/data/fachada.jpg', " +
                "'image/jpeg', 'Fachada', 0, '2026-10-15T18:00:01Z', NULL)"
        )
    }

    // ─── volcado byte a byte ───────────────────────────────────────────────────

    private fun tablasDeUsuario(db: SupportSQLiteDatabase): List<String> = db.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name"
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun esquema(db: SupportSQLiteDatabase): Map<String, String?> = db.query(
        "SELECT name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name != 'room_master_table'"
    )
        .use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) } }

    private fun columnas(db: SupportSQLiteDatabase, tabla: String): List<String> =
        db.query("PRAGMA table_info(`$tabla`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    /** Por tabla, cada fila como `rowid|typeof:quote|...`, en orden de `rowid`. */
    private fun volcado(
        db: SupportSQLiteDatabase,
        tablas: List<String>,
        columnasDe: (String) -> List<String> = { columnas(db, it) }
    ): Map<String, List<String>> = tablas.associateWith { tabla ->
        val cols = columnasDe(tabla)
        assertTrue("la tabla $tabla debe tener columnas", cols.isNotEmpty())
        val select = cols.joinToString(" || '|' || ") { "typeof(`$it`) || ':' || quote(`$it`)" }
        db.query("SELECT rowid || '|' || $select FROM `$tabla` ORDER BY rowid").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
    }
}
