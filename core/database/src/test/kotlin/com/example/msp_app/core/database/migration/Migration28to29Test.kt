package com.example.msp_app.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_28_29
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-28-29-test.db"
private const val OLD_VERSION = 28
private const val NEW_VERSION = 29
private const val PAGO_RECIBIDO_ID_INDEX = "index_Payment_PAGO_RECIBIDO_ID"

/**
 * Migración 28→29 validada de punta a punta contra los JSON reales: la base se
 * crea desde el `28.json` exportado, se siembra con el estado que un
 * dispositivo en campo ya tiene, se aplica la `Migration` REAL —no una copia de
 * su SQL— y Room valida el esquema resultante contra el `29.json`.
 *
 * Cubre los dos arreglos que viajan en esta migración:
 *
 *  - `cobranza_sync_state.AFTER_ID`: la mitad del cursor `(UPDATED_AT, PK)` que
 *    el cliente no persistía. Sin ella, cada corrida reprocesa desde el inicio
 *    el grupo de filas empatadas en `UPDATED_AT` — con el backfill que dejó
 *    1,835,734 de 2,173,422 filas compartiendo un solo valor, ese grupo es el
 *    historial completo y la paginación nunca sale de él.
 *  - `index_Payment_PAGO_RECIBIDO_ID`: la 26→27 agregó la columna sin índice.
 *
 * Qué rompería si fallara: un `ALTER TABLE` mal escrito, un `AFTER_ID` NOT NULL
 * sin default (Room lo rechaza al validar), un índice olvidado, o cualquier
 * futura edición que recreara la tabla y tirara los cursores del cobrador.
 */
class Migration28to29Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `los cursores preexistentes sobreviven y estrenan AFTER_ID en cero`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            db.execSQL(
                """
                INSERT INTO cobranza_sync_state
                    (RESOURCE, ZONA_CLIENTE_ID, CURSOR, LAST_SYNCED_AT, LAST_ERROR, EPOCH)
                VALUES
                    ('ventas', 21, '2026-08-14T18:25:13.456789Z', '2026-08-14T18:25:20Z', NULL, 3),
                    ('pagos', 21, '2026-08-14T18:24:00.000000Z', '2026-08-14T18:25:20Z', NULL, 3)
                """.trimIndent()
            )
        }

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_28_29
        )

        migrated.query(
            "SELECT ZONA_CLIENTE_ID, CURSOR, LAST_SYNCED_AT, EPOCH, AFTER_ID " +
                "FROM cobranza_sync_state ORDER BY RESOURCE"
        ).use { cursor ->
            assertEquals("las dos filas preexistentes deben seguir ahí", 2, cursor.count)

            // Orden alfabético por RESOURCE: 'pagos' antes que 'ventas'.
            assertTrue(cursor.moveToFirst())
            assertEquals(21, cursor.getInt(0))
            assertEquals("2026-08-14T18:24:00.000000Z", cursor.getString(1))
            assertEquals("2026-08-14T18:25:20Z", cursor.getString(2))
            assertEquals("la generación aplicada no se toca", 3, cursor.getInt(3))
            assertEquals("AFTER_ID arranca en 0: desde el inicio del grupo", 0, cursor.getInt(4))

            assertTrue(cursor.moveToNext())
            assertEquals("2026-08-14T18:25:13.456789Z", cursor.getString(1))
            assertEquals(0, cursor.getInt(4))
        }
        migrated.close()
    }

    @Test
    fun `AFTER_ID es NOT NULL con default 0 y admite el valor que escribe el sync`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).close()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_28_29
        )

        // Un INSERT que omite AFTER_ID (la forma en que el código viejo
        // escribía) tiene que seguir siendo válido y caer en 0.
        migrated.execSQL(
            """
            INSERT INTO cobranza_sync_state
                (RESOURCE, ZONA_CLIENTE_ID, CURSOR, LAST_SYNCED_AT, LAST_ERROR, EPOCH)
            VALUES ('ventas', 21, '2026-08-14T19:00:00Z', '2026-08-14T19:00:05Z', NULL, NULL)
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO cobranza_sync_state
                (RESOURCE, ZONA_CLIENTE_ID, CURSOR, LAST_SYNCED_AT, LAST_ERROR, EPOCH, AFTER_ID)
            VALUES ('pagos', 21, '2026-08-14T19:00:00Z', '2026-08-14T19:00:05Z', NULL, NULL, 15808629)
            """.trimIndent()
        )

        migrated.query(
            "SELECT AFTER_ID FROM cobranza_sync_state ORDER BY RESOURCE"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "la PK del último pago aplicado se guarda tal cual",
                15808629,
                cursor.getInt(0)
            )
            assertTrue(cursor.moveToNext())
            assertEquals("sin valor explícito, el default es 0", 0, cursor.getInt(0))
        }

        migrated.query("PRAGMA table_info(cobranza_sync_state)").use { cursor ->
            var seen = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) != "AFTER_ID") continue
                seen = true
                assertEquals("INTEGER", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                assertEquals(
                    "AFTER_ID debe ser NOT NULL: 0 ya significa 'desde el inicio'",
                    1,
                    cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                )
                assertEquals("0", cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")))
            }
            assertTrue("la columna AFTER_ID debe existir tras migrar", seen)
        }
        migrated.close()
    }

    @Test
    fun `la migracion crea el indice que la 26 a 27 dejo pendiente`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            db.query("PRAGMA index_list(Payment)").use { cursor ->
                assertTrue(
                    "en v28 el índice NO existe todavía — ese es el defecto D7",
                    indexNames(cursor).none { it == PAGO_RECIBIDO_ID_INDEX }
                )
            }
        }

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_28_29
        )

        migrated.query("PRAGMA index_list(Payment)").use { cursor ->
            assertTrue(
                "tras la 28→29 el índice sobre PAGO_RECIBIDO_ID debe existir",
                indexNames(cursor).contains(PAGO_RECIBIDO_ID_INDEX)
            )
        }
        migrated.query("PRAGMA index_info($PAGO_RECIBIDO_ID_INDEX)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "PAGO_RECIBIDO_ID",
                cursor.getString(cursor.getColumnIndexOrThrow("name"))
            )
            assertEquals("un solo campo indexado", 1, cursor.count)
        }
        migrated.close()
    }

    private fun indexNames(cursor: android.database.Cursor): List<String> {
        val names = mutableListOf<String>()
        while (cursor.moveToNext()) {
            names.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        return names
    }
}
