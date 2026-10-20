package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_35_36
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-35-36-test.db"
private const val OLD_VERSION = 35
private const val NEW_VERSION = 36

/**
 * E-APP-084: la 35→36 sólo crea `capturas_en_curso` (vacía, con su índice) y no
 * mueve ningún pago ni ninguna venta.
 */
class Migration35to36Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `E-APP-084 la 35 a 36 crea capturas_en_curso vacia y el pago sembrado queda identico`() {
        val antes = migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            db.execSQL(INSERT_PAGO)
            pagos(db)
        }

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_35_36
        )
        val despues = pagos(migrada)
        val marcadores = migrada.query("SELECT COUNT(*) FROM capturas_en_curso").use {
            it.moveToFirst()
            it.getInt(0)
        }
        migrada.close()

        assertEquals("control positivo: el volcado ve el pago sembrado", 1, antes.size)
        assertEquals(antes, despues)
        assertEquals(0, marcadores)
    }

    @Test
    fun `E-APP-084 MIGRATION_35_36 esta registrada en la configuracion real que usa produccion`() {
        migrationTestHelper.createDatabase(
            MIGRATION_DB,
            OLD_VERSION
        ).use { it.execSQL(INSERT_PAGO) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val abierta = AppDatabase.buildDatabase(context, context.getDatabasePath(MIGRATION_DB).path)
            .allowMainThreadQueries()
            .build()
        try {
            val db = abierta.openHelper.readableDatabase
            assertEquals(NEW_VERSION, db.version)
            assertEquals(1, pagos(db).size)
        } finally {
            abierta.close()
        }
    }

    private fun pagos(db: androidx.sqlite.db.SupportSQLiteDatabase): List<String> =
        db.query("SELECT * FROM Payment ORDER BY ID").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add((0 until c.columnCount).joinToString("|") { "${c.getString(it)}" })
                }
            }
        }

    private companion object {
        const val INSERT_PAGO =
            "INSERT INTO Payment (ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO, " +
                "GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID, COBRADOR_ID, " +
                "FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE) VALUES ('p-1', " +
                "'Rosa Elena Martinez Vazquez', 77188, 0, '2026-10-20T10:00:00Z', 0, 220.0, 0.0, 0.0, " +
                "5021, 77, 157, 25, 'Victoria Flores Olmedo')"
    }
}
