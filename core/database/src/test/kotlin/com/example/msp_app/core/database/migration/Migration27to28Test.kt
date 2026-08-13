package com.example.msp_app.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_27_28
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-27-28-test.db"
private const val OLD_VERSION = 27
private const val NEW_VERSION = 28

/**
 * Migración 27→28 (columna `EPOCH` en `cobranza_sync_state`) validada de punta
 * a punta: la base se crea desde el `27.json` REAL exportado, se siembra con el
 * estado que un dispositivo en campo ya tiene, se aplica la `Migration`
 * REAL — no una copia de su SQL — y Room valida el esquema resultante contra
 * el `28.json`. Es la primera migración del proyecto con JSON de versión
 * anterior disponible, así que es la primera que puede probarse así.
 *
 * Qué rompería si fallara: un `ALTER TABLE` mal escrito, un `EPOCH` declarado
 * NOT NULL sin default (Room lo rechazaría al validar), o cualquier futura
 * edición de esta migración que recreara la tabla y tirara los cursores del
 * cobrador — que obligaría a un resync completo silencioso de toda la zona.
 */
class Migration27to28Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `los cursores preexistentes sobreviven y estrenan EPOCH nulo`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            db.execSQL(
                """
                INSERT INTO cobranza_sync_state
                    (RESOURCE, ZONA_CLIENTE_ID, CURSOR, LAST_SYNCED_AT, LAST_ERROR)
                VALUES
                    ('ventas', 21, '2026-08-10T18:25:13.456789Z', '2026-08-10T18:25:20Z', NULL),
                    ('pagos', 21, '2026-08-10T18:24:00.000000Z', '2026-08-10T18:25:20Z', 'timeout')
                """.trimIndent()
            )
        }

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_27_28
        )

        migrated.query(
            "SELECT ZONA_CLIENTE_ID, CURSOR, LAST_SYNCED_AT, LAST_ERROR, EPOCH " +
                "FROM cobranza_sync_state ORDER BY RESOURCE"
        ).use { cursor ->
            assertEquals("las dos filas preexistentes deben seguir ahí", 2, cursor.count)

            // Orden alfabético por RESOURCE: 'pagos' antes que 'ventas'.
            assertTrue(cursor.moveToFirst())
            assertEquals(21, cursor.getInt(0))
            assertEquals("2026-08-10T18:24:00.000000Z", cursor.getString(1))
            assertEquals("2026-08-10T18:25:20Z", cursor.getString(2))
            assertEquals("timeout", cursor.getString(3))
            assertTrue("EPOCH arranca NULL: nunca se aplicó una generación", cursor.isNull(4))

            assertTrue(cursor.moveToNext())
            assertEquals(21, cursor.getInt(0))
            assertEquals("2026-08-10T18:25:13.456789Z", cursor.getString(1))
            assertTrue(cursor.isNull(3))
            assertTrue("EPOCH arranca NULL: nunca se aplicó una generación", cursor.isNull(4))
        }
        migrated.close()
    }
}
