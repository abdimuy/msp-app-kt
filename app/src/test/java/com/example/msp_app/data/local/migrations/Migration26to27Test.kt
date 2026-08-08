package com.example.msp_app.data.local.migrations

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Migration26to27Test : RobolectricTestBase() {

    private lateinit var db: SQLiteDatabase
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbFile = context.getDatabasePath("migration_26_27_test.db")
        dbFile.parentFile?.mkdirs()
        db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        createV26Schema()
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

    private fun createV26Schema() {
        db.execSQL(
            """
            CREATE TABLE Payment (
                ID TEXT PRIMARY KEY NOT NULL,
                COBRADOR TEXT NOT NULL,
                DOCTO_CC_ACR_ID INTEGER NOT NULL,
                DOCTO_CC_ID INTEGER NOT NULL,
                FECHA_HORA_PAGO TEXT NOT NULL,
                GUARDADO_EN_MICROSIP INTEGER NOT NULL,
                IMPORTE REAL NOT NULL,
                LAT REAL,
                LNG REAL,
                CLIENTE_ID INTEGER NOT NULL,
                COBRADOR_ID INTEGER NOT NULL,
                FORMA_COBRO_ID INTEGER NOT NULL,
                ZONA_CLIENTE_ID INTEGER NOT NULL,
                NOMBRE_CLIENTE TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_Payment_DOCTO_CC_ACR_ID ON Payment (DOCTO_CC_ACR_ID)")
        db.execSQL("CREATE INDEX index_Payment_DOCTO_CC_ID ON Payment (DOCTO_CC_ID)")
        db.execSQL("CREATE INDEX index_Payment_FECHA_HORA_PAGO ON Payment (FECHA_HORA_PAGO)")
    }

    private fun runMigration() {
        // Misma sentencia que MIGRATION_26_27.
        db.execSQL("ALTER TABLE Payment ADD COLUMN PAGO_RECIBIDO_ID TEXT")
    }

    @Test
    fun `migration adds PAGO_RECIBIDO_ID column`() {
        runMigration()

        val cursor = db.rawQuery("PRAGMA table_info(Payment)", null)
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        assertTrue("PAGO_RECIBIDO_ID column should exist", columns.contains("PAGO_RECIBIDO_ID"))
    }

    @Test
    fun `migration preserves existing rows and leaves PAGO_RECIBIDO_ID null`() {
        db.execSQL(
            """
            INSERT INTO Payment (
                ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO,
                GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID,
                COBRADOR_ID, FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE
            ) VALUES (
                '15808629', 'Rosa Elena Martinez Vazquez', 100, 101, '2026-06-01T09:00:00Z',
                1, 350.0, NULL, NULL, 4821,
                7, 157, 21, 'Guadalupe Hernandez Soto'
            )
            """.trimIndent()
        )

        runMigration()

        val cursor = db.rawQuery("SELECT * FROM Payment WHERE ID = '15808629'", null)
        assertTrue("row should still exist after migration", cursor.moveToFirst())
        assertEquals(
            "Guadalupe Hernandez Soto",
            cursor.getString(cursor.getColumnIndexOrThrow("NOMBRE_CLIENTE"))
        )
        assertTrue(
            "PAGO_RECIBIDO_ID must be null for pre-existing rows",
            cursor.isNull(cursor.getColumnIndexOrThrow("PAGO_RECIBIDO_ID"))
        )
        cursor.close()
    }

    @Test
    fun `new rows can set PAGO_RECIBIDO_ID after migration`() {
        runMigration()

        db.execSQL(
            """
            INSERT INTO Payment (
                ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO,
                GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID,
                COBRADOR_ID, FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE,
                PAGO_RECIBIDO_ID
            ) VALUES (
                '15808630', 'Rosa Elena Martinez Vazquez', 100, 101, '2026-06-01T09:05:00Z',
                1, 350.0, NULL, NULL, 4821,
                7, 157, 21, 'Guadalupe Hernandez Soto',
                'uuid-x'
            )
            """.trimIndent()
        )

        val cursor = db.rawQuery("SELECT PAGO_RECIBIDO_ID FROM Payment WHERE ID = '15808630'", null)
        assertTrue(cursor.moveToFirst())
        assertEquals("uuid-x", cursor.getString(0))
        cursor.close()
    }
}
