package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_29_30
import com.example.msp_app.core.database.migrations.MIGRATION_30_31
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-29-a-31-test.db"
private const val V29 = 29
private const val V31 = 31

private const val SEEDED_SALE_ID = "sale-migracion-29-a-31"
private const val SEEDED_PRODUCT_ARTICULO_ID = 4821
private const val SEEDED_COMBO_ID = "combo-migracion-29-a-31"
private const val SEEDED_IMAGE_ID = "img-migracion-29-a-31"
private const val SEEDED_VISIT_ID = "visita-migracion-29-a-31"
private const val SEEDED_SALE_CLIENTE = "Rosa Elena Martinez Vazquez"

/**
 * **La cadena completa: v29 → v31, pasando por las DOS migraciones.**
 *
 * Es la prueba que la integración del 2026-09-21 exige y que ninguna de las
 * dos ramas podía escribir por separado: cada una probaba SU 29→30 contra el
 * `30.json` que ella misma exportaba. Tras renumerar (ver el KDoc de
 * `MIGRATION_30_31`) existe por primera vez un salto de dos tramos, y el
 * teléfono que el dueño va a actualizar hoy está justamente en v29 — ninguna
 * de las dos v30 salió nunca a la flota, así que **v29 → v31 es el único
 * camino que la actualización va a recorrer de verdad**.
 *
 * Lo que se afirma:
 *
 * 1. Una venta capturada sin señal, con su producto, su combo y su foto —lo
 *    que un teléfono trae encima cuando el dueño actualiza— sobrevive los dos
 *    tramos con cada campo intacto. Es el peor daño posible de este dominio:
 *    dinero cobrado en la calle que desaparece al actualizar la app.
 * 2. Las columnas de las DOS migraciones existen al final: las de `Visit`
 *    (promesa y cita, tramo 29→30) y las de `local_sale` (el candado, tramo
 *    30→31), más las cinco tablas nuevas del primer tramo.
 * 3. El salto funciona también por el camino REAL de producción
 *    (`AppDatabase.buildDatabase`), que es quien tiene que encontrar la ruta
 *    de 29 a 31 con las dos migraciones registradas. Una de las dos fuera de
 *    `addMigrations` deja la app sin abrir en un teléfono con ventas adentro,
 *    y las pruebas que pasan las migraciones a mano no lo verían.
 */
class Migration29a31Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `una venta pendiente y sus hijos sobreviven los dos tramos de v29 a v31`() {
        seedV29()

        val migrada = migrarHastaV31()

        migrada.query(
            """
            SELECT NOMBRE_CLIENTE, PRECIO_TOTAL, ENVIADO, PARCIALIDAD, ENGANCHE
            FROM local_sale WHERE LOCAL_SALE_ID = ?
            """.trimIndent(),
            arrayOf(SEEDED_SALE_ID)
        ).use { cursor ->
            assertTrue("la venta sembrada en v29 debe seguir ahí en v31", cursor.moveToFirst())
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
                "el producto de la venta debe sobrevivir los dos tramos",
                cursor.moveToFirst()
            )
            assertEquals("Colchon Queen", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }

        migrada.query(
            "SELECT NOMBRE_COMBO FROM local_sale_combos WHERE LOCAL_SALE_ID = ? AND COMBO_ID = ?",
            arrayOf(SEEDED_SALE_ID, SEEDED_COMBO_ID)
        ).use { cursor ->
            assertTrue("el combo de la venta debe sobrevivir los dos tramos", cursor.moveToFirst())
            assertEquals("Combo Recamara Completa", cursor.getString(0))
        }

        migrada.query(
            "SELECT IMAGE_URI FROM sale_image WHERE LOCAL_SALE_ID = ? AND LOCAL_SALE_IMAGE_ID = ?",
            arrayOf(SEEDED_SALE_ID, SEEDED_IMAGE_ID)
        ).use { cursor ->
            assertTrue("la foto de la venta debe sobrevivir los dos tramos", cursor.moveToFirst())
            assertEquals("content://images/evidencia-001.jpg", cursor.getString(0))
        }

        migrada.close()
    }

    @Test
    fun `al final de la cadena existen las columnas de las DOS migraciones`() {
        seedV29()

        val migrada = migrarHastaV31()

        // Tramo 29→30 (plan `pagos-y-visitas`): promesa y cita sobre `Visit`.
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

        // Tramo 30→31 (plan "Corregir una venta antes de que suba"): el candado.
        val columnasDeVenta = nombresDeColumna(migrada, "local_sale")
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

        // Y las cinco tablas nuevas del primer tramo, que el segundo no toca.
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

        // La visita sembrada conserva su nota y estrena las columnas nuevas vacías.
        migrada.query(
            "SELECT NOTA, PROMESA_FECHA, CITA_FECHA FROM Visit WHERE ID = ?",
            arrayOf(SEEDED_VISIT_ID)
        ).use { cursor ->
            assertTrue("la visita sembrada en v29 debe seguir ahí", cursor.moveToFirst())
            assertEquals("No estaba, vuelvo el jueves", cursor.getString(0))
            assertTrue("PROMESA_FECHA estrena NULL", cursor.isNull(1))
            assertTrue("CITA_FECHA estrena NULL", cursor.isNull(2))
        }

        migrada.close()
    }

    /**
     * El camino REAL: `AppDatabase.buildDatabase` es la única fuente de verdad
     * del builder de producción. Si cualquiera de las dos migraciones faltara
     * en su `addMigrations`, Room no encontraría ruta de 29 a 31 y `.build()`
     * tronaría al primer acceso — exactamente la falla que vería un teléfono
     * con ventas pendientes adentro.
     */
    @Test
    fun `abrir por el camino de produccion lleva una base v29 hasta v31`() {
        seedV29()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbPath = context.getDatabasePath(MIGRATION_DB).path

        val abierta = AppDatabase.buildDatabase(context, dbPath)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(
                "abrir por el camino real de produccion debe terminar en v31",
                V31,
                abierta.openHelper.readableDatabase.version
            )

            val venta = runBlocking { abierta.localSaleDao().getSaleById(SEEDED_SALE_ID) }
            assertEquals(
                "la venta sembrada en v29 debe sobrevivir a la apertura real",
                SEEDED_SALE_CLIENTE,
                venta?.NOMBRE_CLIENTE
            )
            assertEquals("REVISION arranca en 0 tras la cadena completa", 0, venta?.REVISION)
        } finally {
            abierta.close()
        }
    }

    private fun migrarHastaV31(): SupportSQLiteDatabase =
        migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            V31,
            true,
            MIGRATION_29_30,
            MIGRATION_30_31
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
