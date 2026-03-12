package com.example.msp_app.data.local.migrations

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.`test-fixtures`.RobolectricTestBase
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Migration20to21Test : RobolectricTestBase() {

    private lateinit var db: SQLiteDatabase
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbFile = context.getDatabasePath("migration_test.db")
        dbFile.parentFile?.mkdirs()
        db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        createV20Schema()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
        if (::dbFile.isInitialized) {
            dbFile.delete()
        }
    }

    private fun createV20Schema() {
        db.execSQL(
            """
            CREATE TABLE garantias (
                ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                EXTERNAL_ID TEXT NOT NULL,
                DOCTO_CC_ID INTEGER NOT NULL,
                ESTADO TEXT NOT NULL,
                DESCRIPCION_FALLA TEXT NOT NULL,
                OBSERVACIONES TEXT,
                UPLOADED INTEGER NOT NULL,
                FECHA_SOLICITUD TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX index_garantias_EXTERNAL_ID ON garantias (EXTERNAL_ID)")
        db.execSQL("CREATE INDEX index_garantias_DOCTO_CC_ID ON garantias (DOCTO_CC_ID)")
        db.execSQL("CREATE INDEX index_garantias_FECHA_SOLICITUD ON garantias (FECHA_SOLICITUD)")
    }

    private fun runMigration() {
        // Execute the same SQL statements as MIGRATION_20_21
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS garantias_new (
                ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                EXTERNAL_ID TEXT NOT NULL,
                DOCTO_CC_ID INTEGER,
                ESTADO TEXT NOT NULL,
                DESCRIPCION_FALLA TEXT NOT NULL,
                OBSERVACIONES TEXT,
                UPLOADED INTEGER NOT NULL,
                FECHA_SOLICITUD TEXT NOT NULL,
                NOMBRE_CLIENTE TEXT,
                NOMBRE_PRODUCTO TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO garantias_new (ID, EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, OBSERVACIONES, UPLOADED, FECHA_SOLICITUD)
            SELECT ID, EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, OBSERVACIONES, UPLOADED, FECHA_SOLICITUD
            FROM garantias
            """.trimIndent()
        )
        db.execSQL("DROP TABLE garantias")
        db.execSQL("ALTER TABLE garantias_new RENAME TO garantias")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_garantias_EXTERNAL_ID ON garantias (EXTERNAL_ID)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_garantias_DOCTO_CC_ID ON garantias (DOCTO_CC_ID)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_garantias_FECHA_SOLICITUD ON garantias (FECHA_SOLICITUD)"
        )
    }

    @Test
    fun `migration preserves existing data`() {
        db.execSQL(
            """
            INSERT INTO garantias (EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, OBSERVACIONES, UPLOADED, FECHA_SOLICITUD)
            VALUES ('ext-001', 100, 'NOTIFICADO', 'Pantalla rota', 'Revisar', 1, '2025-01-15T10:00:00Z')
            """.trimIndent()
        )

        runMigration()

        val cursor = db.rawQuery("SELECT * FROM garantias WHERE EXTERNAL_ID = 'ext-001'", null)
        assertTrue("Row should exist after migration", cursor.moveToFirst())
        assertEquals("ext-001", cursor.getString(cursor.getColumnIndexOrThrow("EXTERNAL_ID")))
        assertEquals(100, cursor.getInt(cursor.getColumnIndexOrThrow("DOCTO_CC_ID")))
        assertEquals("NOTIFICADO", cursor.getString(cursor.getColumnIndexOrThrow("ESTADO")))
        assertEquals(
            "Pantalla rota",
            cursor.getString(cursor.getColumnIndexOrThrow("DESCRIPCION_FALLA"))
        )
        assertEquals("Revisar", cursor.getString(cursor.getColumnIndexOrThrow("OBSERVACIONES")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("UPLOADED")))
        assertEquals(
            "2025-01-15T10:00:00Z",
            cursor.getString(cursor.getColumnIndexOrThrow("FECHA_SOLICITUD"))
        )
        cursor.close()
    }

    @Test
    fun `migration adds new columns as null for existing rows`() {
        db.execSQL(
            """
            INSERT INTO garantias (EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, OBSERVACIONES, UPLOADED, FECHA_SOLICITUD)
            VALUES ('ext-002', 200, 'RECOLECTADO', 'Motor falla', NULL, 0, '2025-02-10T14:30:00Z')
            """.trimIndent()
        )

        runMigration()

        val cursor = db.rawQuery("SELECT * FROM garantias WHERE EXTERNAL_ID = 'ext-002'", null)
        assertTrue(cursor.moveToFirst())
        assertTrue(
            "NOMBRE_CLIENTE should be null",
            cursor.isNull(cursor.getColumnIndexOrThrow("NOMBRE_CLIENTE"))
        )
        assertTrue(
            "NOMBRE_PRODUCTO should be null",
            cursor.isNull(cursor.getColumnIndexOrThrow("NOMBRE_PRODUCTO"))
        )
        cursor.close()
    }

    @Test
    fun `migration makes DOCTO_CC_ID nullable`() {
        runMigration()

        db.execSQL(
            """
            INSERT INTO garantias (EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, UPLOADED, FECHA_SOLICITUD, NOMBRE_CLIENTE, NOMBRE_PRODUCTO)
            VALUES ('ext-003', NULL, 'NOTIFICADO', 'Falla general', 0, '2025-03-01T08:00:00Z', 'Cliente Test', 'Producto Test')
            """.trimIndent()
        )

        val cursor = db.rawQuery("SELECT * FROM garantias WHERE EXTERNAL_ID = 'ext-003'", null)
        assertTrue("Row with null DOCTO_CC_ID should exist", cursor.moveToFirst())
        assertTrue(
            "DOCTO_CC_ID should be null",
            cursor.isNull(cursor.getColumnIndexOrThrow("DOCTO_CC_ID"))
        )
        assertEquals(
            "Cliente Test",
            cursor.getString(cursor.getColumnIndexOrThrow("NOMBRE_CLIENTE"))
        )
        assertEquals(
            "Producto Test",
            cursor.getString(cursor.getColumnIndexOrThrow("NOMBRE_PRODUCTO"))
        )
        cursor.close()
    }

    @Test
    fun `migration preserves multiple rows`() {
        db.execSQL(
            """
            INSERT INTO garantias (EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, OBSERVACIONES, UPLOADED, FECHA_SOLICITUD)
            VALUES ('ext-a', 10, 'NOTIFICADO', 'Falla A', NULL, 1, '2025-01-01T00:00:00Z')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO garantias (EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, OBSERVACIONES, UPLOADED, FECHA_SOLICITUD)
            VALUES ('ext-b', 20, 'ENTREGADO', 'Falla B', 'Obs B', 1, '2025-01-02T00:00:00Z')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO garantias (EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, OBSERVACIONES, UPLOADED, FECHA_SOLICITUD)
            VALUES ('ext-c', 30, 'RECOLECTADO', 'Falla C', NULL, 0, '2025-01-03T00:00:00Z')
            """.trimIndent()
        )

        runMigration()

        val cursor = db.rawQuery("SELECT COUNT(*) FROM garantias", null)
        assertTrue(cursor.moveToFirst())
        assertEquals(3, cursor.getInt(0))
        cursor.close()
    }

    @Test
    fun `migration recreates indices`() {
        runMigration()

        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='garantias'",
            null
        )
        val indices = mutableListOf<String>()
        while (cursor.moveToNext()) {
            indices.add(cursor.getString(0))
        }
        cursor.close()

        assertTrue(
            "EXTERNAL_ID index should exist",
            indices.contains("index_garantias_EXTERNAL_ID")
        )
        assertTrue(
            "DOCTO_CC_ID index should exist",
            indices.contains("index_garantias_DOCTO_CC_ID")
        )
        assertTrue(
            "FECHA_SOLICITUD index should exist",
            indices.contains("index_garantias_FECHA_SOLICITUD")
        )
    }

    @Test
    fun `migration preserves autoincrement ID`() {
        db.execSQL(
            """
            INSERT INTO garantias (EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, OBSERVACIONES, UPLOADED, FECHA_SOLICITUD)
            VALUES ('ext-id-1', 50, 'NOTIFICADO', 'Falla', NULL, 1, '2025-06-01T00:00:00Z')
            """.trimIndent()
        )

        runMigration()

        val cursor = db.rawQuery("SELECT ID FROM garantias WHERE EXTERNAL_ID = 'ext-id-1'", null)
        assertTrue(cursor.moveToFirst())
        val id = cursor.getInt(0)
        assertTrue("ID should be a positive autoincremented value", id > 0)
        cursor.close()
    }
}
