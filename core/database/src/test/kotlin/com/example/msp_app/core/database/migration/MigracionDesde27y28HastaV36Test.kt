package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ULTIMA_VERSION = 36

/**
 * E-APP-063: un teléfono con una base de cualquier versión exportada (v27, v28,
 * v29, v33, v34, v35) abre por el camino REAL de producción (`AppDatabase.buildDatabase`)
 * hasta la última versión, **exactamente** v36 (la 35→36 crea
 * `capturas_en_curso`, E-APP-084), y cada columna que las filas de
 * **todas** sus tablas de datos (`Payment`, `Visit`, `sales`, `local_sale`…) ya
 * tenían conserva valor y tipo de almacenamiento (`typeof` + `quote`).
 *
 * La siembra sale del propio esquema de la versión de origen (`PRAGMA
 * table_info`): toda columna recibe un valor, y las que admiten nulo alternan
 * con `NULL`, así que ninguna columna existente queda sin cubrir.
 */
class MigracionDesde27y28HastaV36Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `E-APP-063 desde v27 por buildDatabase llega a v36 con todas las tablas intactas`() =
        verificarDesde(
            27
        )

    @Test
    fun `E-APP-063 desde v28 por buildDatabase llega a v36 con todas las tablas intactas`() =
        verificarDesde(
            28
        )

    @Test
    fun `desde v29 por buildDatabase llega a v36 con todas las tablas intactas`() = verificarDesde(
        29
    )

    @Test
    fun `desde v33 por buildDatabase llega a v36 con todas las tablas intactas`() = verificarDesde(
        33
    )

    @Test
    fun `desde v34 por buildDatabase llega a v36 con todas las tablas intactas`() = verificarDesde(
        34
    )

    @Test
    fun `E-APP-084 desde v35 por buildDatabase llega a v36 con todas las tablas intactas`() =
        verificarDesde(
            35
        )

    private fun verificarDesde(version: Int) {
        val nombre = "migracion-$version-a-$ULTIMA_VERSION.db"
        val antes = migrationTestHelper.createDatabase(nombre, version).use { db ->
            val tablas = tablasDeOrigen(db)
            tablas.forEach { sembrar(db, it) }
            tablas.associateWith { volcado(db, it, columnas(db, it)) }
        }
        assertTrue(
            "v$version: Payment y Visit entran",
            antes.keys.containsAll(listOf("Payment", "Visit"))
        )
        assertTrue(
            "control positivo: hay filas que comparar en cada tabla ${antes.mapValues { it.value.size }}",
            antes.values.all { it.size == FILAS }
        )
        val columnasDeOrigen = antes.mapValues { (_, filas) -> filas.first().keys.toList() }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val abierta = AppDatabase.buildDatabase(context, context.getDatabasePath(nombre).path)
            .allowMainThreadQueries()
            .build()
        try {
            val db = abierta.openHelper.readableDatabase
            assertEquals(
                "v$version debe terminar EXACTAMENTE en v$ULTIMA_VERSION",
                ULTIMA_VERSION,
                db.version
            )
            antes.keys.forEach { tabla ->
                assertEquals(
                    "v$version: $tabla conserva cada columna de origen",
                    antes.getValue(tabla),
                    volcado(db, tabla, columnasDeOrigen.getValue(tabla))
                )
            }
        } finally {
            abierta.close()
        }
    }

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

    private fun columnas(db: SupportSQLiteDatabase, tabla: String): List<String> =
        db.query("PRAGMA table_info(`$tabla`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    private fun volcado(
        db: SupportSQLiteDatabase,
        tabla: String,
        cols: List<String>
    ): List<Map<String, String>> {
        val select = cols.joinToString { "typeof(`$it`) || ':' || quote(`$it`)" }
        return db.query("SELECT $select FROM `$tabla` ORDER BY rowid").use { c ->
            buildList {
                while (c.moveToNext()) add(cols.indices.associate { cols[it] to c.getString(it) })
            }
        }
    }

    /**
     * Todas las tablas de datos de la versión de origen, las que tienen llave
     * foránea al final. Una columna con FK recibe el mismo valor que su columna
     * referida en la fila `i` porque en este esquema se llaman igual
     * (`ARTICULO_ID`, `LOCAL_SALE_ID`): la siembra es por nombre y tipo.
     */
    private fun tablasDeOrigen(db: SupportSQLiteDatabase): List<String> {
        val tablas = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name"
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        val conFk = tablas.filter { t ->
            db.query("PRAGMA foreign_key_list(`$t`)").use { it.count > 0 }
        }.toSet()
        return tablas.sortedBy { it in conFk }
    }

    private companion object {
        const val FILAS = 4
    }
}
