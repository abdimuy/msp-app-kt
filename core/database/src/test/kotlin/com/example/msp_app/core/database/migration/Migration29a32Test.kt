package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_29_30
import com.example.msp_app.core.database.migrations.MIGRATION_30_31
import com.example.msp_app.core.database.migrations.MIGRATION_31_32
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-29-a-32-test.db"
private const val V29 = 29
private const val V32 = 32

private const val SEEDED_SALE_ID = "sale-migracion-29-a-32"
private const val SEEDED_PRODUCT_ARTICULO_ID = 4821
private const val SEEDED_COMBO_ID = "combo-migracion-29-a-32"
private const val SEEDED_IMAGE_ID = "img-migracion-29-a-32"
private const val SEEDED_VISIT_ID = "visita-migracion-29-a-32"
private const val SEEDED_SALE_CLIENTE = "Rosa Elena Martinez Vazquez"

/**
 * **La cadena completa: v29 → v32, pasando por las TRES migraciones.**
 *
 * Extiende (no duplica) la prueba que la integración del 2026-09-21 exigió
 * para v29→v31 — se llamaba `Migration29a31Test`; se renombra a este archivo
 * al sumarle el tramo del nivel 2 en vez de escribir un archivo hermano, para
 * no tener dos pruebas afirmando la misma supervivencia con nombres
 * distintos. El teléfono que el dueño actualiza hoy sigue estando en v29, así
 * que **v29 → v32 es el único camino que la actualización recorre de
 * verdad**.
 *
 * Lo que se afirma:
 *
 * 1. Una venta capturada sin señal, con su producto, su combo y su foto —lo
 *    que un teléfono trae encima cuando el dueño actualiza— sobrevive los
 *    tres tramos con cada campo intacto. Es el peor daño posible de este
 *    dominio: dinero cobrado en la calle que desaparece al actualizar la app.
 * 2. Las columnas de las TRES migraciones existen al final: las de `Visit`
 *    (promesa y cita, tramo 29→30), las del candado único de `local_sale`
 *    (tramo 30→31, nivel 1) y las del estado del servidor + cola de
 *    correcciones remotas de `local_sale` (tramo 31→32, nivel 2), más las
 *    cinco tablas nuevas del primer tramo.
 * 3. El salto funciona también por el camino REAL de producción
 *    (`AppDatabase.buildDatabase`), que es quien tiene que encontrar la ruta
 *    de 29 a 32 con las tres migraciones registradas. Cualquiera de las tres
 *    fuera de `addMigrations` deja la app sin abrir en un teléfono con
 *    ventas adentro, y las pruebas que pasan las migraciones a mano no lo
 *    verían.
 */
class Migration29a32Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `una venta pendiente y sus hijos sobreviven los tres tramos de v29 a v32`() {
        seedV29()

        val migrada = migrarHastaV32()

        migrada.query(
            """
            SELECT NOMBRE_CLIENTE, PRECIO_TOTAL, ENVIADO, PARCIALIDAD, ENGANCHE
            FROM local_sale WHERE LOCAL_SALE_ID = ?
            """.trimIndent(),
            arrayOf(SEEDED_SALE_ID)
        ).use { cursor ->
            assertTrue("la venta sembrada en v29 debe seguir ahí en v32", cursor.moveToFirst())
            assertEquals(SEEDED_SALE_CLIENTE, cursor.getString(0))
            assertEquals(6800.0, cursor.getDouble(1), 0.0)
            assertEquals("ENVIADO no debe mutar por efecto de migrar", 0, cursor.getInt(2))
            assertEquals(850.0, cursor.getDouble(3), 0.0)
            assertEquals(500.0, cursor.getDouble(4), 0.0)
        }

        migrada.query(
            "SELECT ARTICULO, CANTIDAD FROM local_sale_products " +
                "WHERE LOCAL_SALE_ID = ? AND ARTICULO_ID = ?",
            arrayOf(SEEDED_SALE_ID, SEEDED_PRODUCT_ARTICULO_ID)
        ).use { cursor ->
            assertTrue(
                "el producto de la venta debe sobrevivir los tres tramos",
                cursor.moveToFirst()
            )
            assertEquals("Colchon Queen", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }

        migrada.query(
            "SELECT NOMBRE_COMBO FROM local_sale_combos WHERE LOCAL_SALE_ID = ? AND COMBO_ID = ?",
            arrayOf(SEEDED_SALE_ID, SEEDED_COMBO_ID)
        ).use { cursor ->
            assertTrue("el combo de la venta debe sobrevivir los tres tramos", cursor.moveToFirst())
            assertEquals("Combo Recamara Completa", cursor.getString(0))
        }

        migrada.query(
            "SELECT IMAGE_URI FROM sale_image WHERE LOCAL_SALE_ID = ? AND LOCAL_SALE_IMAGE_ID = ?",
            arrayOf(SEEDED_SALE_ID, SEEDED_IMAGE_ID)
        ).use { cursor ->
            assertTrue("la foto de la venta debe sobrevivir los tres tramos", cursor.moveToFirst())
            assertEquals("content://images/evidencia-001.jpg", cursor.getString(0))
        }

        migrada.close()
    }

    @Test
    fun `al final de la cadena existen las columnas de las TRES migraciones`() {
        seedV29()

        val migrada = migrarHastaV32()

        assertColumnasDelTramo29a30(migrada)
        val columnasDeVenta = nombresDeColumna(migrada, "local_sale")
        assertColumnasDelTramo30a31(columnasDeVenta)
        assertColumnasDelTramo31a32(columnasDeVenta)
        assertTablasNuevasDelTramo29a30(migrada)
        assertVisitaSembradaSobrevive(migrada)
        assertVentaSembradaEstrenaColumnasDelNivel2(migrada)

        migrada.close()
    }

    // Tramo 29→30 (plan `pagos-y-visitas`): promesa y cita sobre `Visit`.
    private fun assertColumnasDelTramo29a30(migrada: SupportSQLiteDatabase) {
        val columnasDeVisit = nombresDeColumna(migrada, "Visit")
        listOf(
            "PROMESA_VENTA_ID",
            "PROMESA_FECHA",
            "PROMESA_MONTO_CENTAVOS",
            "CITA_FECHA",
            "CITA_HORA"
        ).forEach {
            assertTrue("falta $it: el tramo 29→30 no corrió", it in columnasDeVisit)
        }
    }

    // Tramo 30→31 (plan "Corregir una venta antes de que suba", nivel 1): el candado.
    private fun assertColumnasDelTramo30a31(columnasDeVenta: List<String>) {
        listOf(
            "CLAIM_ID",
            "CLAIM_KIND",
            "CLAIMED_AT",
            "REVISION",
            "CORRECCION_NO_ENVIADA",
            "REVISION_POSTEADA"
        ).forEach {
            assertTrue("falta $it: el tramo 30→31 no corrió", it in columnasDeVenta)
        }
    }

    // Tramo 31→32 (plan "Corregir una venta DESPUÉS de que subió", nivel 2):
    // estado del servidor + cola de correcciones remotas.
    private fun assertColumnasDelTramo31a32(columnasDeVenta: List<String>) {
        listOf(
            "SERVER_SITUACION",
            "SERVER_SINCRONIZACION",
            "SERVER_VERSION",
            "SERVER_STATE_AT",
            "CORRECCION_REMOTA_PENDIENTE",
            "CORRECCION_REMOTA_ESTADO",
            "REVISION_REMOTA_ENVIADA"
        ).forEach {
            assertTrue("falta $it: el tramo 31→32 no corrió", it in columnasDeVenta)
        }
    }

    // Las cinco tablas nuevas del primer tramo, que los otros dos no tocan.
    private fun assertTablasNuevasDelTramo29a30(migrada: SupportSQLiteDatabase) {
        listOf(
            "visita_imagenes",
            "pago_imagenes",
            "visita_recomendaciones",
            "cliente_ficha",
            "cliente_ficha_senales"
        ).forEach { tabla ->
            migrada.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(tabla)
            ).use { cursor ->
                assertTrue("falta la tabla $tabla del tramo 29→30", cursor.moveToFirst())
            }
        }
    }

    // La visita sembrada conserva su nota y estrena las columnas nuevas vacías.
    private fun assertVisitaSembradaSobrevive(migrada: SupportSQLiteDatabase) {
        migrada.query(
            "SELECT NOTA, PROMESA_FECHA, CITA_FECHA FROM Visit WHERE ID = ?",
            arrayOf(SEEDED_VISIT_ID)
        ).use { cursor ->
            assertTrue("la visita sembrada en v29 debe seguir ahí", cursor.moveToFirst())
            assertEquals("No estaba, vuelvo el jueves", cursor.getString(0))
            assertTrue("PROMESA_FECHA estrena NULL", cursor.isNull(1))
            assertTrue("CITA_FECHA estrena NULL", cursor.isNull(2))
        }
    }

    // La venta sembrada estrena las siete columnas del nivel 2 vacías/en su default.
    private fun assertVentaSembradaEstrenaColumnasDelNivel2(migrada: SupportSQLiteDatabase) {
        migrada.query(
            """
            SELECT SERVER_SITUACION, CORRECCION_REMOTA_PENDIENTE, REVISION_REMOTA_ENVIADA
            FROM local_sale WHERE LOCAL_SALE_ID = ?
            """.trimIndent(),
            arrayOf(SEEDED_SALE_ID)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("SERVER_SITUACION estrena NULL: nunca se leyó el servidor", cursor.isNull(0))
            assertEquals(
                "CORRECCION_REMOTA_PENDIENTE estrena 0",
                0,
                cursor.getInt(1)
            )
            assertTrue("REVISION_REMOTA_ENVIADA estrena NULL", cursor.isNull(2))
        }
    }

    /**
     * El camino REAL: `AppDatabase.buildDatabase` es la única fuente de verdad
     * del builder de producción. Si cualquiera de las tres migraciones
     * faltara en su `addMigrations`, Room no encontraría ruta de 29 a 32 y
     * `.build()` tronaría al primer acceso — exactamente la falla que vería
     * un teléfono con ventas pendientes adentro.
     */
    @Test
    fun `abrir por el camino de produccion lleva una base v29 hasta v32`() {
        seedV29()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbPath = context.getDatabasePath(MIGRATION_DB).path

        val abierta = AppDatabase.buildDatabase(context, dbPath)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(
                "abrir por el camino real de produccion debe terminar en v32",
                V32,
                abierta.openHelper.readableDatabase.version
            )

            val venta = runBlocking { abierta.localSaleDao().getSaleById(SEEDED_SALE_ID) }
            assertEquals(
                "la venta sembrada en v29 debe sobrevivir a la apertura real",
                SEEDED_SALE_CLIENTE,
                venta?.NOMBRE_CLIENTE
            )
            assertEquals("REVISION arranca en 0 tras la cadena completa", 0, venta?.REVISION)
            assertEquals(
                "CORRECCION_REMOTA_PENDIENTE arranca en 0 tras la cadena completa",
                false,
                venta?.CORRECCION_REMOTA_PENDIENTE
            )
        } finally {
            abierta.close()
        }
    }

    private fun migrarHastaV32(): SupportSQLiteDatabase =
        migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            V32,
            true,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32
        )

    private fun nombresDeColumna(db: SupportSQLiteDatabase, tabla: String): List<String> =
        db.query("PRAGMA table_info(`$tabla`)").use { cursor ->
            val nombres = mutableListOf<String>()
            val indice = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                nombres += cursor.getString(indice)
            }
            nombres
        }

    private fun seedV29() {
        migrationTestHelper.createDatabase(MIGRATION_DB, V29).use { db ->
            db.execSQL(
                """
                INSERT INTO local_sale (
                    LOCAL_SALE_ID, NOMBRE_CLIENTE, FECHA_VENTA, LATITUD, LONGITUD, DIRECCION,
                    PARCIALIDAD, ENGANCHE, TELEFONO, FREC_PAGO, AVAL_O_RESPONSABLE, NOTA,
                    DIA_COBRANZA, PRECIO_TOTAL, TIEMPO_A_CORTO_PLAZOMESES, MONTO_A_CORTO_PLAZO,
                    MONTO_DE_CONTADO, ENVIADO
                ) VALUES (
                    '$SEEDED_SALE_ID', '$SEEDED_SALE_CLIENTE', '2026-09-18T15:30:00Z',
                    19.043415, -98.198234, 'Privada de las Rosas 45',
                    850.0, 500.0, '2221234567', 'SEMANAL', 'Juan Martinez Vazquez', NULL,
                    'MARTES', 6800.0, 8, 6300.0, 5800.0, 0
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
                    2200.0, 2000.0, 1800.0, NULL, NULL
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
                    4300.0, 4000.0, NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO sale_image (
                    LOCAL_SALE_IMAGE_ID, LOCAL_SALE_ID, IMAGE_URI, FECHA_SUBIDA, SERVER_UUID
                ) VALUES (
                    '$SEEDED_IMAGE_ID', '$SEEDED_SALE_ID', 'content://images/evidencia-001.jpg',
                    '2026-09-18T15:31:00Z', NULL
                )
                """.trimIndent()
            )
            seedVisitaV29(db)
        }
    }

    private fun seedVisitaV29(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO Visit (
                ID, CLIENTE_ID, COBRADOR, COBRADOR_ID, FECHA, FORMA_COBRO_ID, LAT, LNG,
                NOTA, TIPO_VISITA, ZONA_CLIENTE_ID, IMPTE_DOCTO_CC_ID, GUARDADO_EN_MICROSIP
            ) VALUES (
                '$SEEDED_VISIT_ID', 9041, 'Gabriel Roque Cardenas', 18,
                '2026-09-18T16:05:00Z', 71, 19.043415, -98.198234,
                'No estaba, vuelvo el jueves', 'NO_ESTABA', 4, 0, 0
            )
            """.trimIndent()
        )
    }
}
