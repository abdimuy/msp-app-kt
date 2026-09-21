package com.example.msp_app.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_29_30
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-29-30-test.db"
private const val OLD_VERSION = 29
private const val NEW_VERSION = 30
private const val SEEDED_SALE_ID = "sale-migracion-29-30"
private const val SEEDED_PRODUCT_ARTICULO_ID = 4821
private const val SEEDED_COMBO_ID = "combo-migracion-001"
private const val SEEDED_IMAGE_ID = "img-migracion-001"

/**
 * Migración 29→30 validada de punta a punta contra los JSON reales (mismo
 * patrón que [Migration28to29Test]): la base se crea desde el `29.json`
 * exportado, se siembra con una venta pendiente REAL de campo — con producto,
 * combo e imagen, exactamente lo que un dispositivo trae cuando el dueño
 * captura sin señal — se aplica la `Migration` REAL, y Room valida el
 * esquema resultante contra el `30.json`.
 *
 * Agrega tres columnas nuevas a `local_sale` para el plan "Corregir una venta
 * antes de que suba" (reclamo de edición con arrendamiento): `EDIT_CLAIM_ID`,
 * `EDIT_CLAIMED_AT` (ambas nullable, nadie ha reclamado nada todavía) y
 * `REVISION` (`NOT NULL DEFAULT 0`, cero correcciones commiteadas). Todo
 * `ALTER TABLE ADD COLUMN`: ninguna tabla se recrea.
 *
 * Qué rompería si este test fallara: cualquier edición a la migración (una
 * columna con NOT NULL sin default, un tipo equivocado) que Room rechazara al
 * validar, o cualquier futura edición que recreara `local_sale` y se llevara
 * de encuentro la venta pendiente y sus hijos — el peor bug posible en este
 * dominio: una venta capturada en la calle que desaparece al actualizar la
 * app.
 */
class Migration29to30Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `las tres columnas del reclamo existen y arrancan libres tras migrar`() {
        seedPendingSaleWithChildren()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_29_30
        )

        migrated.query(
            "SELECT EDIT_CLAIM_ID, EDIT_CLAIMED_AT, REVISION FROM local_sale WHERE LOCAL_SALE_ID = ?",
            arrayOf(SEEDED_SALE_ID)
        ).use { cursor ->
            assertTrue("la venta sembrada antes de migrar debe seguir ahí", cursor.moveToFirst())
            assertTrue(
                "EDIT_CLAIM_ID debe quedar NULL: nadie ha reclamado nada todavía",
                cursor.isNull(0)
            )
            assertTrue(
                "EDIT_CLAIMED_AT debe quedar NULL junto con el reclamo",
                cursor.isNull(1)
            )
            assertEquals(
                "REVISION arranca en 0: cero correcciones commiteadas",
                0,
                cursor.getInt(2)
            )
        }
        migrated.close()
    }

    @Test
    fun `REVISION es NOT NULL con default 0 y admite el incremento que escribe el commit`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).close()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_29_30
        )

        migrated.query("PRAGMA table_info(local_sale)").use { cursor ->
            var seen = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) != "REVISION") continue
                seen = true
                assertEquals("INTEGER", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                assertEquals(
                    "REVISION debe ser NOT NULL: 0 ya significa 'sin correcciones'",
                    1,
                    cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                )
                assertEquals("0", cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")))
            }
            assertTrue("la columna REVISION debe existir tras migrar", seen)
        }
        migrated.close()
    }

    @Test
    fun `la venta pendiente y sus hijos sobreviven la migracion 29 a 30`() {
        seedPendingSaleWithChildren()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_29_30
        )

        migrated.query(
            "SELECT NOMBRE_CLIENTE, PRECIO_TOTAL, ENVIADO FROM local_sale WHERE LOCAL_SALE_ID = ?",
            arrayOf(SEEDED_SALE_ID)
        ).use { cursor ->
            assertTrue("la venta debe sobrevivir", cursor.moveToFirst())
            assertEquals("Rosa Elena Martinez Vazquez", cursor.getString(0))
            assertEquals(6800.0, cursor.getDouble(1), 0.0)
            assertEquals("ENVIADO no debe mutar por efecto de migrar", 0, cursor.getInt(2))
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
            assertEquals("content://images/evidencia-001.jpg", cursor.getString(0))
        }

        migrated.close()
    }

    private fun seedPendingSaleWithChildren() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            db.execSQL(
                """
                INSERT INTO local_sale (
                    LOCAL_SALE_ID, NOMBRE_CLIENTE, FECHA_VENTA, LATITUD, LONGITUD, DIRECCION,
                    PARCIALIDAD, ENGANCHE, TELEFONO, FREC_PAGO, AVAL_O_RESPONSABLE, NOTA,
                    DIA_COBRANZA, PRECIO_TOTAL, TIEMPO_A_CORTO_PLAZOMESES, MONTO_A_CORTO_PLAZO,
                    MONTO_DE_CONTADO, ENVIADO
                ) VALUES (
                    '$SEEDED_SALE_ID', 'Rosa Elena Martinez Vazquez', '2026-09-18T15:30:00Z',
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
        }
    }
}
