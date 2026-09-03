package com.example.msp_app.core.database.migration

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_29_30
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-29-30-test.db"
private const val OLD_VERSION = 29
private const val NEW_VERSION = 30

private const val PAGO_SIN_SUBIR_ID = "7f1c2f2e-0d4a-4a1e-9a2b-1c0f5f7c9a01"
private const val PAGO_SUBIDO_ID = "7f1c2f2e-0d4a-4a1e-9a2b-1c0f5f7c9a02"
private const val PAGO_SIN_SUBIR_IMPORTE = 1287.35
private const val PAGO_SUBIDO_IMPORTE = 950.00

private const val VISITA_VIEJA_ID = "b3d9a6c4-5f21-4c73-8f10-2a6d3e9b4c11"
private const val VISITA_NUEVA_ID = "b3d9a6c4-5f21-4c73-8f10-2a6d3e9b4c12"
private const val VISITA_NOTA = "La cita ha sido reagendada para el 12 de septiembre"
private const val VISITA_FORMA_VIEJA_ID = "b3d9a6c4-5f21-4c73-8f10-2a6d3e9b4c13"
private const val VISITA_CON_PROMESA_ID = "b3d9a6c4-5f21-4c73-8f10-2a6d3e9b4c14"

/**
 * Migración 29→30 (Task 26, la ÚNICA migración del plan `pagos-y-visitas`)
 * validada de punta a punta: la base se crea desde el `29.json` REAL
 * exportado, se siembra con lo que un teléfono de campo ya tiene —pagos sin
 * subir, pagos ya en Microsip, visitas con su nota de texto libre, cursores de
 * sincronización—, se aplica el objeto `Migration` REAL (no una copia de su
 * SQL) y Room valida el esquema resultante contra el `30.json`.
 *
 * Lo que estas pruebas clavan, y que es lo caro de esta tarea:
 *
 * 1. **Nada preexistente se pierde ni cambia.** Cada campo de cada fila
 *    sembrada se relee y se compara valor por valor después de migrar,
 *    incluido el `IMPORTE` del pago que todavía no llega al servidor. Una
 *    migración que recreara `Payment` o `Visit` en vez de usar `ADD COLUMN`
 *    muere aquí.
 * 2. **Las columnas nuevas estrenan `NULL`**, que es la lectura correcta de
 *    "esta visita no dejó promesa ni cita".
 * 3. **El esquema le sirve a las Tasks 19, 22, 23 y 24**: 0..N comprobantes
 *    por visita y por pago con el `id_<n>` que exige el multipart, el brazo
 *    del experimento con default `'tratamiento'`, y la ficha con el catálogo
 *    cerrado separado de la nota libre.
 */
class Migration29to30Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private fun seedV29(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO Payment (
                ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO,
                GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID,
                COBRADOR_ID, FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE,
                PAGO_RECIBIDO_ID
            ) VALUES
                ('$PAGO_SIN_SUBIR_ID', 'Efrain Dominguez Reyes', 48213, 91027,
                 '2026-08-28T16:45:00Z', 0, $PAGO_SIN_SUBIR_IMPORTE, 19.043415, -98.198234,
                 30144, 7, 157, 21, 'Araceli Jimenez Cortes', NULL),
                ('$PAGO_SUBIDO_ID', 'Efrain Dominguez Reyes', 48214, 91028,
                 '2026-08-27T11:05:00Z', 1, $PAGO_SUBIDO_IMPORTE, NULL, NULL,
                 30190, 7, 157, 21, 'Jose Luis Mendoza Aguilar',
                 'c0ffee00-0000-4000-8000-000000000001')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO Visit (
                ID, CLIENTE_ID, COBRADOR, COBRADOR_ID, FECHA, FORMA_COBRO_ID,
                LAT, LNG, NOTA, TIPO_VISITA, ZONA_CLIENTE_ID,
                IMPTE_DOCTO_CC_ID, GUARDADO_EN_MICROSIP
            ) VALUES
                ('$VISITA_VIEJA_ID', 30144, 'Efrain Dominguez Reyes', 7,
                 '2026-08-28T17:10:00Z', 157, 19.043415, -98.198234,
                 '$VISITA_NOTA', 'PIDE_TIEMPO', 21, 91027, 1),
                ('$VISITA_NUEVA_ID', 30190, 'Efrain Dominguez Reyes', 7,
                 '2026-08-28T18:20:00Z', 157, 19.041000, -98.196000,
                 NULL, 'NO_SE_ENCONTRABA', 21, 91028, 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO cliente (CLIENTE_ID, NOMBRE, ESTATUS, CAUSA_SUSP)
            VALUES (30144, 'Araceli Jimenez Cortes', 'A', NULL)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO cobranza_sync_state
                (RESOURCE, ZONA_CLIENTE_ID, CURSOR, LAST_SYNCED_AT, LAST_ERROR, EPOCH, AFTER_ID)
            VALUES ('pagos', 21, '2026-08-28T18:25:13.456789Z', '2026-08-28T18:25:20Z',
                    NULL, 3, 15808629)
            """.trimIndent()
        )
    }

    private fun migrate(): SupportSQLiteDatabase {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db -> seedV29(db) }
        return migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_29_30
        )
    }

    private fun columnNames(db: SupportSQLiteDatabase, table: String): List<String> {
        val names = mutableListOf<String>()
        db.query("PRAGMA table_info($table)").use { cursor ->
            while (cursor.moveToNext()) {
                names.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        return names
    }

    private fun Cursor.nullableString(index: Int): String? =
        if (isNull(index)) null else getString(index)

    @Test
    fun `el pago que aun no llega al servidor sobrevive con cada campo intacto`() {
        val migrated = migrate()

        migrated.query(
            "SELECT COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO, " +
                "GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID, COBRADOR_ID, " +
                "FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE, PAGO_RECIBIDO_ID " +
                "FROM Payment WHERE ID = ?",
            arrayOf(PAGO_SIN_SUBIR_ID)
        ).use { cursor ->
            assertTrue("el pago sin subir debe seguir ahí después de migrar", cursor.moveToFirst())
            assertEquals("Efrain Dominguez Reyes", cursor.getString(0))
            assertEquals(48213, cursor.getInt(1))
            assertEquals(91027, cursor.getInt(2))
            assertEquals("2026-08-28T16:45:00Z", cursor.getString(3))
            assertEquals("migrar no puede marcarlo como subido", 0, cursor.getInt(4))
            assertEquals(PAGO_SIN_SUBIR_IMPORTE, cursor.getDouble(5), 0.0)
            assertEquals(19.043415, cursor.getDouble(6), 0.0)
            assertEquals(-98.198234, cursor.getDouble(7), 0.0)
            assertEquals(30144, cursor.getInt(8))
            assertEquals(7, cursor.getInt(9))
            assertEquals(157, cursor.getInt(10))
            assertEquals(21, cursor.getInt(11))
            assertEquals("Araceli Jimenez Cortes", cursor.getString(12))
            assertTrue("PAGO_RECIBIDO_ID sigue NULL: nunca se subió", cursor.isNull(13))
        }

        migrated.query("SELECT COUNT(*) FROM Payment").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("los dos pagos sembrados siguen ahí", 2, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun `Payment y cobranza_sync_state no cambian ni una columna`() {
        val antes = mutableListOf<String>()
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            seedV29(db)
            antes.addAll(columnNames(db, "Payment"))
        }

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_29_30
        )

        assertEquals(
            "la tabla del dinero no aparece en el lado izquierdo de ninguna sentencia",
            antes,
            columnNames(migrated, "Payment")
        )
        migrated.query(
            "SELECT CURSOR, EPOCH, AFTER_ID FROM cobranza_sync_state WHERE RESOURCE = 'pagos'"
        ).use { cursor ->
            assertTrue("el cursor del cobrador sobrevive", cursor.moveToFirst())
            assertEquals("2026-08-28T18:25:13.456789Z", cursor.getString(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals(15808629, cursor.getInt(2))
        }
        migrated.close()
    }

    @Test
    fun `las visitas preexistentes conservan su nota y estrenan promesa y cita en NULL`() {
        val migrated = migrate()

        migrated.query(
            "SELECT ID, NOTA, TIPO_VISITA, IMPTE_DOCTO_CC_ID, GUARDADO_EN_MICROSIP, " +
                "PROMESA_VENTA_ID, PROMESA_FECHA, PROMESA_MONTO_CENTAVOS, CITA_FECHA, CITA_HORA " +
                "FROM Visit ORDER BY ID"
        ).use { cursor ->
            assertEquals("las dos visitas sembradas siguen ahí", 2, cursor.count)

            assertTrue(cursor.moveToFirst())
            assertEquals(VISITA_VIEJA_ID, cursor.getString(0))
            assertEquals("el texto libre del cobrador no se toca", VISITA_NOTA, cursor.getString(1))
            assertEquals("PIDE_TIEMPO", cursor.getString(2))
            assertEquals(91027, cursor.getInt(3))
            assertEquals(1, cursor.getInt(4))
            assertTrue("PROMESA_VENTA_ID arranca NULL", cursor.isNull(5))
            assertTrue("PROMESA_FECHA arranca NULL", cursor.isNull(6))
            assertTrue("PROMESA_MONTO_CENTAVOS arranca NULL", cursor.isNull(7))
            assertTrue("CITA_FECHA arranca NULL", cursor.isNull(8))
            assertTrue("CITA_HORA arranca NULL", cursor.isNull(9))

            assertTrue(cursor.moveToNext())
            assertEquals(VISITA_NUEVA_ID, cursor.getString(0))
            assertNull("la visita sin nota sigue sin nota", cursor.nullableString(1))
            assertEquals("la visita aún sin subir sigue pendiente", 0, cursor.getInt(4))
        }
        migrated.close()
    }

    private fun insertVisitFormaVieja(db: SupportSQLiteDatabase, id: String) {
        db.execSQL(
            """
            INSERT INTO Visit (
                ID, CLIENTE_ID, COBRADOR, COBRADOR_ID, FECHA, FORMA_COBRO_ID,
                LAT, LNG, NOTA, TIPO_VISITA, ZONA_CLIENTE_ID,
                IMPTE_DOCTO_CC_ID, GUARDADO_EN_MICROSIP
            ) VALUES (
                '$id', 30144, 'Efrain Dominguez Reyes', 7,
                '2026-08-29T09:00:00Z', 157, 19.043415, -98.198234, NULL,
                'CASA_CERRADA_CON_CANDADO', 21, 91027, 0
            )
            """.trimIndent()
        )
    }

    /** Promesa y cita completas: 4,150.75 pesos = 415075 centavos. */
    private fun insertVisitConPromesaYCita(db: SupportSQLiteDatabase, id: String) {
        db.execSQL(
            """
            INSERT INTO Visit (
                ID, CLIENTE_ID, COBRADOR, COBRADOR_ID, FECHA, FORMA_COBRO_ID,
                LAT, LNG, NOTA, TIPO_VISITA, ZONA_CLIENTE_ID,
                IMPTE_DOCTO_CC_ID, GUARDADO_EN_MICROSIP,
                PROMESA_VENTA_ID, PROMESA_FECHA, PROMESA_MONTO_CENTAVOS,
                CITA_FECHA, CITA_HORA
            ) VALUES (
                '$id', 30190, 'Efrain Dominguez Reyes', 7,
                '2026-08-29T10:30:00Z', 157, 19.041000, -98.196000, NULL,
                'PIDE_TIEMPO', 21, 91028, 0,
                91028, '2026-09-04', 415075, '2026-09-04', '18:30'
            )
            """.trimIndent()
        )
    }

    @Test
    fun `las columnas nuevas de Visit guardan la promesa y la cita`() {
        val migrated = migrate()
        insertVisitConPromesaYCita(migrated, VISITA_CON_PROMESA_ID)

        migrated.query(
            "SELECT PROMESA_VENTA_ID, PROMESA_FECHA, PROMESA_MONTO_CENTAVOS, " +
                "CITA_FECHA, CITA_HORA FROM Visit WHERE ID = ?",
            arrayOf(VISITA_CON_PROMESA_ID)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(91028, cursor.getInt(0))
            assertEquals("2026-09-04", cursor.getString(1))
            assertEquals(
                "el monto viaja en centavos enteros: exacto, sin flotante de por medio",
                415075L,
                cursor.getLong(2)
            )
            assertEquals("2026-09-04", cursor.getString(3))
            assertEquals("18:30", cursor.getString(4))
        }
        migrated.close()
    }

    @Test
    fun `las cinco columnas nuevas de Visit son nullable y sin DEFAULT`() {
        val migrated = migrate()
        // Un INSERT con la forma vieja —sin las columnas nuevas— tiene que
        // seguir siendo válido: es lo que hace el código que hoy está en la calle.
        insertVisitFormaVieja(migrated, VISITA_FORMA_VIEJA_ID)

        migrated.query(
            "SELECT PROMESA_FECHA, CITA_HORA FROM Visit WHERE ID = ?",
            arrayOf(VISITA_FORMA_VIEJA_ID)
        ).use { cursor ->
            assertTrue("el INSERT con la forma vieja sigue siendo válido", cursor.moveToFirst())
            assertTrue("sin valor explícito la promesa queda NULL, no en cero", cursor.isNull(0))
            assertTrue(cursor.isNull(1))
        }

        migrated.query("PRAGMA table_info(Visit)").use { cursor ->
            var vistas = 0
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (name !in NUEVAS_COLUMNAS_VISIT) continue
                vistas++
                assertEquals(
                    "$name debe ser nullable: las visitas viejas no tienen promesa ni cita",
                    0,
                    cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                )
                assertNull(
                    "$name no lleva DEFAULT: NULL es 'no hubo', y no se confunde con cero",
                    cursor.nullableString(cursor.getColumnIndexOrThrow("dflt_value"))
                )
            }
            assertEquals("las cinco columnas nuevas deben existir", 5, vistas)
        }
        migrated.close()
    }

    @Test
    fun `una visita y un pago admiten cero N comprobantes con el id que exige el multipart`() {
        val migrated = migrate()

        // Dos comprobantes sobre la MISMA visita: es lo que una columna única
        // no podría representar y el servidor sí acepta ("0..N comprobantes").
        migrated.execSQL(
            """
            INSERT INTO visita_imagenes
                (ID, VISITA_ID, URI, MIME, DESCRIPCION, ORDEN, CREADA_EN, SUBIDA_EN)
            VALUES
                ('11111111-1111-4111-8111-111111111111', '$VISITA_NUEVA_ID',
                 'content://msp/visita/1.jpg', 'image/jpeg', 'fachada', 0,
                 '2026-08-28T18:21:00Z', NULL),
                ('22222222-2222-4222-8222-222222222222', '$VISITA_NUEVA_ID',
                 'content://msp/visita/2.jpg', 'image/jpeg', NULL, 1,
                 '2026-08-28T18:21:30Z', NULL)
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO pago_imagenes
                (ID, PAGO_ID, URI, MIME, DESCRIPCION, ORDEN, CREADA_EN, SUBIDA_EN)
            VALUES ('33333333-3333-4333-8333-333333333333', '$PAGO_SIN_SUBIR_ID',
                    'content://msp/pago/1.jpg', 'image/jpeg', 'recibo', 0,
                    '2026-08-28T16:46:00Z', NULL)
            """.trimIndent()
        )

        migrated.query(
            "SELECT ID, ORDEN, DESCRIPCION FROM visita_imagenes WHERE VISITA_ID = ? ORDER BY ORDEN",
            arrayOf(VISITA_NUEVA_ID)
        ).use { cursor ->
            assertEquals("una visita puede llevar más de un comprobante", 2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "la PK es el UUID que el teléfono manda como id_<n>",
                "11111111-1111-4111-8111-111111111111",
                cursor.getString(0)
            )
            assertEquals(0, cursor.getInt(1))
            assertEquals("fachada", cursor.getString(2))
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(1))
            assertTrue("la descripción es opcional en el contrato", cursor.isNull(2))
        }
        migrated.query(
            "SELECT COUNT(*) FROM pago_imagenes WHERE PAGO_ID = ?",
            arrayOf(PAGO_SIN_SUBIR_ID)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query(
            "SELECT COUNT(*) FROM visita_imagenes WHERE VISITA_ID = ?",
            arrayOf(VISITA_VIEJA_ID)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("cero comprobantes también es válido", 0, cursor.getInt(0))
        }

        assertForeignKey(migrated, "visita_imagenes", "Visit", "VISITA_ID")
        assertForeignKey(migrated, "pago_imagenes", "Payment", "PAGO_ID")
        migrated.close()
    }

    private fun assertForeignKey(
        db: SupportSQLiteDatabase,
        table: String,
        parent: String,
        column: String
    ) {
        db.query("PRAGMA foreign_key_list($table)").use { cursor ->
            assertTrue("$table debe declarar su FK", cursor.moveToFirst())
            assertEquals(parent, cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals(column, cursor.getString(cursor.getColumnIndexOrThrow("from")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }
    }

    @Test
    fun `la recomendacion se guarda aunque no haya visita y su brazo default es tratamiento`() {
        val migrated = migrate()

        // Recomendación atendida: se muestra y termina en visita.
        migrated.execSQL(
            """
            INSERT INTO visita_recomendaciones
                (ID, CLIENTE_ID, VENTA_ID, COBRADOR_ID, GENERADA_EN, POSICION,
                 MOTIVO, ALGORITMO, VISITA_ID)
            VALUES ('44444444-4444-4444-8444-444444444444', 30190, 91028, 7,
                    '2026-08-28T08:00:00Z', 0, 'cercania', 'cercania_v1', '$VISITA_NUEVA_ID')
            """.trimIndent()
        )
        // Recomendación de control: NO se muestra, y por eso NUNCA produce una
        // visita. Es la fila que columnas en `Visit` no podrían registrar.
        migrated.execSQL(
            """
            INSERT INTO visita_recomendaciones
                (ID, CLIENTE_ID, VENTA_ID, COBRADOR_ID, GENERADA_EN, POSICION,
                 MOTIVO, ALGORITMO, GRUPO, VISITA_ID)
            VALUES ('55555555-5555-4555-8555-555555555555', 30144, NULL, 7,
                    '2026-08-28T08:00:00Z', 1, 'mejor_hora', 'cercania_v1', 'control', NULL)
            """.trimIndent()
        )

        migrated.query(
            "SELECT GRUPO, VISITA_ID, VENTA_ID, POSICION, MOTIVO, ALGORITMO " +
                "FROM visita_recomendaciones ORDER BY POSICION"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "sin valor explícito el brazo es 'tratamiento'",
                "tratamiento",
                cursor.getString(0)
            )
            assertEquals(VISITA_NUEVA_ID, cursor.getString(1))
            assertEquals(91028, cursor.getInt(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals("cercania", cursor.getString(4))
            assertEquals("cercania_v1", cursor.getString(5))

            assertTrue(cursor.moveToNext())
            assertEquals("el brazo de control se guarda tal cual", "control", cursor.getString(0))
            assertTrue("el control no produce visita, y aun así queda registrado", cursor.isNull(1))
            assertTrue("una sugerencia puede ser del cliente completo", cursor.isNull(2))
        }
        migrated.close()
    }

    @Test
    fun `la ficha guarda el catalogo cerrado y la nota libre por separado`() {
        val migrated = migrate()

        migrated.execSQL(
            """
            INSERT INTO cliente_ficha (CLIENTE_ID, NOTA, ACTUALIZADA_EN, COBRADOR_ID)
            VALUES (30144, 'Casa azul, porton negro, no hay timbre',
                    '2026-08-28T17:12:00Z', 7)
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO cliente_ficha_senales (CLIENTE_ID, SENAL, ACTUALIZADA_EN)
            VALUES (30144, 'TRABAJA_DE_NOCHE', '2026-08-28T17:12:00Z'),
                   (30144, 'ATIENDE_FAMILIAR', '2026-08-28T17:12:00Z')
            """.trimIndent()
        )
        // Un cliente puede tener señales SIN nota: son dos campos con trabajos
        // distintos, no dos mitades del mismo.
        migrated.execSQL(
            """
            INSERT INTO cliente_ficha_senales (CLIENTE_ID, SENAL, ACTUALIZADA_EN)
            VALUES (30190, 'PERRO_BRAVO', '2026-08-28T18:22:00Z')
            """.trimIndent()
        )

        migrated.query(
            "SELECT NOTA FROM cliente_ficha WHERE CLIENTE_ID = 30144"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Casa azul, porton negro, no hay timbre", cursor.getString(0))
        }
        migrated.query(
            "SELECT SENAL FROM cliente_ficha_senales WHERE CLIENTE_ID = 30144 ORDER BY SENAL"
        ).use { cursor ->
            assertEquals("el catálogo admite varias señales por cliente", 2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("ATIENDE_FAMILIAR", cursor.getString(0))
            assertTrue(cursor.moveToNext())
            assertEquals("TRABAJA_DE_NOCHE", cursor.getString(0))
        }
        migrated.query(
            "SELECT COUNT(*) FROM cliente_ficha WHERE CLIENTE_ID = 30190"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("señales sin nota es un estado válido", 0, cursor.getInt(0))
        }

        // La ficha NO cuelga de `cliente`: esa tabla se borra entera en cada
        // sincronización del catálogo (`ClienteDao.replaceAll`), y la ficha
        // tiene que sobrevivirlo.
        migrated.execSQL("DELETE FROM cliente")
        migrated.query("SELECT COUNT(*) FROM cliente_ficha").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "borrar el catálogo de clientes no puede llevarse la ficha",
                1,
                cursor.getInt(0)
            )
        }
        migrated.query("SELECT COUNT(*) FROM cliente_ficha_senales").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun `las cinco tablas nuevas existen y ninguna existente desaparece`() {
        val antes = mutableListOf<String>()
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            seedV29(db)
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name"
            ).use { cursor ->
                while (cursor.moveToNext()) antes.add(cursor.getString(0))
            }
            NUEVAS_TABLAS.forEach { tabla ->
                assertFalse("en v29 `$tabla` NO existe todavía", antes.contains(tabla))
            }
        }

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_29_30
        )

        val despues = mutableListOf<String>()
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name"
        ).use { cursor ->
            while (cursor.moveToNext()) despues.add(cursor.getString(0))
        }
        assertTrue(
            "ninguna tabla de v29 puede desaparecer: $antes contra $despues",
            despues.containsAll(antes)
        )
        NUEVAS_TABLAS.forEach { tabla ->
            assertTrue("tras migrar `$tabla` debe existir", despues.contains(tabla))
        }
        migrated.close()
    }

    private companion object {
        val NUEVAS_COLUMNAS_VISIT = setOf(
            "PROMESA_VENTA_ID",
            "PROMESA_FECHA",
            "PROMESA_MONTO_CENTAVOS",
            "CITA_FECHA",
            "CITA_HORA"
        )

        val NUEVAS_TABLAS = listOf(
            "visita_imagenes",
            "pago_imagenes",
            "visita_recomendaciones",
            "cliente_ficha",
            "cliente_ficha_senales"
        )
    }
}
