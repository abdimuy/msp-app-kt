package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_31_32
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-31-32-test.db"
private const val OLD_VERSION = 31
private const val NEW_VERSION = 32
private const val SEEDED_SALE_ID = "sale-migracion-31-32"
private const val SEEDED_SALE_CLIENTE = "Rosa Elena Martinez Vazquez"
private const val SEEDED_PRODUCT_ARTICULO_ID = 4821
private const val SEEDED_COMBO_ID = "combo-migracion-31-32"
private const val SEEDED_IMAGE_ID = "img-migracion-31-32"

/**
 * Migración 31→32 validada de punta a punta contra los JSON reales (mismo
 * patrón que [Migration30to31Test]): la base se crea desde el `31.json`
 * exportado, se siembra con una venta YA ENVIADA — el caso que le importa a
 * esta migración, porque las siete columnas nuevas sólo tienen sentido para
 * una venta que el servidor ya tiene —, se aplica la `Migration` REAL, y
 * Room valida el esquema resultante contra el `32.json`.
 *
 * Agrega siete columnas nuevas a `local_sale` para el plan "Corregir una
 * venta DESPUÉS de que subió, mientras siga en borrador" (nivel 2, Task A1):
 * el estado del servidor que el teléfono guarda para decidir si una venta
 * enviada sigue siendo corregible (`SERVER_SITUACION`,
 * `SERVER_SINCRONIZACION`, `SERVER_VERSION`, `SERVER_STATE_AT`, las cuatro
 * nullable — nadie ha leído el servidor todavía) y la cola de correcciones
 * remotas (`CORRECCION_REMOTA_PENDIENTE` `NOT NULL DEFAULT 0`,
 * `CORRECCION_REMOTA_ESTADO` nullable, `REVISION_REMOTA_ENVIADA` nullable
 * sin default). Todo `ALTER TABLE ADD COLUMN`: ninguna tabla se recrea.
 *
 * Qué rompería si este test fallara: cualquier edición a la migración (una
 * columna con NOT NULL sin default, un tipo equivocado) que Room rechazara al
 * validar, o cualquier futura edición que recreara `local_sale` y se llevara
 * de encuentro una venta YA ENVIADA y sus hijos.
 */
class Migration31to32Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `las siete columnas del nivel 2 existen y arrancan sin dato tras migrar`() {
        seedSentSaleWithChildren()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_31_32
        )

        migrated.query(
            """
            SELECT SERVER_SITUACION, SERVER_SINCRONIZACION, SERVER_VERSION, SERVER_STATE_AT,
                   CORRECCION_REMOTA_PENDIENTE, CORRECCION_REMOTA_ESTADO, REVISION_REMOTA_ENVIADA
            FROM local_sale WHERE LOCAL_SALE_ID = ?
            """.trimIndent(),
            arrayOf(SEEDED_SALE_ID)
        ).use { cursor ->
            assertTrue("la venta sembrada antes de migrar debe seguir ahí", cursor.moveToFirst())
            assertTrue(
                "SERVER_SITUACION debe quedar NULL: nunca se ha leído el servidor",
                cursor.isNull(0)
            )
            assertTrue("SERVER_SINCRONIZACION debe quedar NULL", cursor.isNull(1))
            assertTrue("SERVER_VERSION debe quedar NULL", cursor.isNull(2))
            assertTrue("SERVER_STATE_AT debe quedar NULL: nunca se leyó", cursor.isNull(3))
            assertEquals(
                "CORRECCION_REMOTA_PENDIENTE arranca en 0: sin corrección remota pendiente todavía",
                0,
                cursor.getInt(4)
            )
            assertTrue(
                "CORRECCION_REMOTA_ESTADO debe quedar NULL: sin incidencia",
                cursor.isNull(5)
            )
            assertTrue(
                "REVISION_REMOTA_ENVIADA debe quedar VACIA: ninguna corrida remota exitosa todavía",
                cursor.isNull(6)
            )
        }
        migrated.close()
    }

    /**
     * El otro lado de la misma regla (mismo argumento que `REVISION_POSTEADA`
     * del nivel 1): una instalación NUEVA no pasa por la migración y también
     * tiene que nacer sin ancla, sin default.
     */
    @Test
    fun `una instalacion nueva tambien nace sin REVISION_REMOTA_ENVIADA y sin default`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fresh = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val columna = try {
            leerColumna(fresh.openHelper.readableDatabase, "REVISION_REMOTA_ENVIADA")
        } finally {
            fresh.close()
        }

        assertNotNull("la columna REVISION_REMOTA_ENVIADA debe existir", columna)
        assertEquals("INTEGER", columna!!.tipo)
        assertEquals(
            "REVISION_REMOTA_ENVIADA debe ser NULLABLE",
            0,
            columna.notNull
        )
        assertTrue(
            "no debe tener default: un 0 por defecto mentiria diciendo que ya hubo una corrida exitosa",
            columna.default == null
        )
    }

    private data class ColumnaSqlite(val tipo: String, val notNull: Int, val default: String?)

    private fun leerColumna(db: SupportSQLiteDatabase, nombre: String): ColumnaSqlite? {
        db.query("PRAGMA table_info(local_sale)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) != nombre) continue
                val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                val tipo = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                val default = if (cursor.isNull(defaultIndex)) {
                    null
                } else {
                    cursor.getString(defaultIndex)
                }
                return ColumnaSqlite(tipo = tipo, notNull = notNull, default = default)
            }
        }
        return null
    }

    /**
     * Cierra el mismo hallazgo que ya cubrió Task 1 del nivel 1 para
     * `MIGRATION_30_31`: nada probaría que `MIGRATION_31_32` está REGISTRADA
     * en `AppDatabase.buildDatabase` si sólo se le pasara la migración a mano
     * a `MigrationTestHelper`. Esta prueba abre por el camino REAL de
     * producción.
     */
    @Test
    fun `MIGRATION_31_32 esta registrada en la configuracion real que usa produccion`() {
        seedSentSaleWithChildren()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbPath = context.getDatabasePath(MIGRATION_DB).path

        val opened = AppDatabase.buildDatabase(context, dbPath)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(
                "abrir por el camino real de produccion debe terminar en v32",
                NEW_VERSION,
                opened.openHelper.readableDatabase.version
            )

            val sale = runBlocking { opened.localSaleDao().getSaleById(SEEDED_SALE_ID) }
            assertEquals(
                "la venta sembrada en v31 debe sobrevivir a la apertura real",
                SEEDED_SALE_CLIENTE,
                sale?.NOMBRE_CLIENTE
            )
            assertEquals(
                "CORRECCION_REMOTA_PENDIENTE arranca en 0 tras la cadena completa",
                false,
                sale?.CORRECCION_REMOTA_PENDIENTE
            )
        } finally {
            opened.close()
        }
    }

    @Test
    fun `CORRECCION_REMOTA_PENDIENTE es NOT NULL con default 0`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).close()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_31_32
        )

        migrated.query("PRAGMA table_info(local_sale)").use { cursor ->
            var seen = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) != "CORRECCION_REMOTA_PENDIENTE") {
                    continue
                }
                seen = true
                assertEquals("INTEGER", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                assertEquals(
                    "CORRECCION_REMOTA_PENDIENTE debe ser NOT NULL",
                    1,
                    cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                )
                assertEquals("0", cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")))
            }
            assertTrue("la columna CORRECCION_REMOTA_PENDIENTE debe existir tras migrar", seen)
        }
        migrated.close()
    }

    @Test
    fun `una venta ya enviada y sus hijos sobreviven la migracion 31 a 32`() {
        seedSentSaleWithChildren()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_31_32
        )

        migrated.query(
            "SELECT NOMBRE_CLIENTE, PRECIO_TOTAL, ENVIADO FROM local_sale WHERE LOCAL_SALE_ID = ?",
            arrayOf(SEEDED_SALE_ID)
        ).use { cursor ->
            assertTrue("la venta debe sobrevivir", cursor.moveToFirst())
            assertEquals(SEEDED_SALE_CLIENTE, cursor.getString(0))
            assertEquals(6800.0, cursor.getDouble(1), 0.0)
            assertEquals("ENVIADO no debe mutar por efecto de migrar", 1, cursor.getInt(2))
        }

        migrated.query(
            "SELECT ARTICULO, CANTIDAD FROM local_sale_products WHERE LOCAL_SALE_ID = ? AND ARTICULO_ID = ?",
            arrayOf(SEEDED_SALE_ID, SEEDED_PRODUCT_ARTICULO_ID)
        ).use { cursor ->
            assertTrue("el producto de la venta debe sobrevivir", cursor.moveToFirst())
            assertEquals("Colchon Queen", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }

        migrated.query(
            "SELECT NOMBRE_COMBO FROM local_sale_combos WHERE LOCAL_SALE_ID = ? AND COMBO_ID = ?",
            arrayOf(SEEDED_SALE_ID, SEEDED_COMBO_ID)
        ).use { cursor ->
            assertTrue("el combo de la venta debe sobrevivir", cursor.moveToFirst())
            assertEquals("Combo Recamara Completa", cursor.getString(0))
        }

        migrated.query(
            "SELECT IMAGE_URI FROM sale_image WHERE LOCAL_SALE_ID = ? AND LOCAL_SALE_IMAGE_ID = ?",
            arrayOf(SEEDED_SALE_ID, SEEDED_IMAGE_ID)
        ).use { cursor ->
            assertTrue("la imagen de la venta debe sobrevivir", cursor.moveToFirst())
            assertEquals("content://images/evidencia-002.jpg", cursor.getString(0))
        }

        migrated.close()
    }

    private fun seedSentSaleWithChildren() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            db.execSQL(
                """
                INSERT INTO local_sale (
                    LOCAL_SALE_ID, NOMBRE_CLIENTE, FECHA_VENTA, LATITUD, LONGITUD, DIRECCION,
                    PARCIALIDAD, ENGANCHE, TELEFONO, FREC_PAGO, AVAL_O_RESPONSABLE, NOTA,
                    DIA_COBRANZA, PRECIO_TOTAL, TIEMPO_A_CORTO_PLAZOMESES, MONTO_A_CORTO_PLAZO,
                    MONTO_DE_CONTADO, ENVIADO, REVISION, CORRECCION_NO_ENVIADA
                ) VALUES (
                    '$SEEDED_SALE_ID', '$SEEDED_SALE_CLIENTE', '2026-09-18T15:30:00Z',
                    19.043415, -98.198234, 'Privada de las Rosas 45',
                    850.0, 500.0, '2221234567', 'SEMANAL', 'Juan Martinez Vazquez', NULL,
                    'MARTES', 6800.0, 8, 6300.0, 5800.0, 1, 0, 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO local_sale_products (
                    LOCAL_SALE_ID, ARTICULO_ID, ARTICULO, CANTIDAD, PRECIO_LISTA,
                    PRECIO_CORTO_PLAZO, PRECIO_CONTADO, COMBO_ID, SERVER_UUID
                ) VALUES (
                    '$SEEDED_SALE_ID', $SEEDED_PRODUCT_ARTICULO_ID, 'Colchon Queen', 1,
                    2200.0, 2000.0, 1800.0, NULL, 'server-uuid-producto-31-32'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO local_sale_combos (
                    COMBO_ID, LOCAL_SALE_ID, NOMBRE_COMBO, PRECIO_LISTA, PRECIO_CORTO_PLAZO,
                    PRECIO_CONTADO, SERVER_UUID
                ) VALUES (
                    '$SEEDED_COMBO_ID', '$SEEDED_SALE_ID', 'Combo Recamara Completa', 4600.0,
                    4300.0, 4000.0, 'server-uuid-combo-31-32'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO sale_image (
                    LOCAL_SALE_IMAGE_ID, LOCAL_SALE_ID, IMAGE_URI, FECHA_SUBIDA, SERVER_UUID
                ) VALUES (
                    '$SEEDED_IMAGE_ID', '$SEEDED_SALE_ID', 'content://images/evidencia-002.jpg',
                    '2026-09-18T15:31:00Z', 'server-uuid-imagen-31-32'
                )
                """.trimIndent()
            )
        }
    }
}
