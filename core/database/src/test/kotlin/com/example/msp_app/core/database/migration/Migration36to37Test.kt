package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_36_37
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-36-37-test.db"
private const val OLD_VERSION = 36
private const val NEW_VERSION = 37
private const val ULTIMA_VERSION = 38

/**
 * E-APP-084: la 36→37 (antes 35→36; renumerada por la secuencia única de
 * versiones Room, P2 = 36, Captura = 37) sólo crea `capturas_en_curso` (vacía,
 * con su índice) y no mueve ningún pago ni ninguna venta.
 */
class Migration36to37Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `E-APP-084 la 36 a 37 crea capturas_en_curso vacia y el pago sembrado queda identico`() {
        val antes = migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            db.execSQL(INSERT_PAGO)
            pagos(db)
        }

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_36_37
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
    fun `E-APP-084 MIGRATION_36_37 esta registrada en la configuracion real que usa produccion`() {
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
            assertEquals(ULTIMA_VERSION, db.version)
            assertEquals(1, pagos(db).size)
        } finally {
            abierta.close()
        }
    }

    /**
     * Se compone con lo que ya hubiera: un teléfono de desarrollo con la 36
     * vieja de Captura (que ya creaba `capturas_en_curso`) pasa por la 36→37
     * sin error y sin perder sus marcadores (`IF NOT EXISTS`).
     */
    @Test
    fun `E-APP-084 la 36 a 37 sobre una base que ya tiene capturas_en_curso conserva sus marcadores`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            MIGRATION_36_37.migrate(db)
            db.execSQL(
                "INSERT INTO capturas_en_curso (ID, TIPO, LLAVE, USUARIO, ESTADO, RESUMEN, " +
                    "CREADA_EN, ACTUALIZADA_EN) VALUES ('m-1', 'ABONO', '77188', " +
                    "'rosa@example.com', 'CAPTURADA', '{}', '2026-10-20T10:00:00Z', " +
                    "'2026-10-20T10:00:00Z')"
            )
        }

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_36_37
        )
        val marcadores = migrada.query("SELECT ID, ESTADO FROM capturas_en_curso").use { c ->
            buildList { while (c.moveToNext()) add("${c.getString(0)}:${c.getString(1)}") }
        }
        migrada.close()

        assertEquals(listOf("m-1:CAPTURADA"), marcadores)
    }

    /**
     * E-APP-092: la 36→37 recrea `overdue_payments_view` sin el abono -2. La 36
     * trae la vista vieja (sin el filtro); si la migración no la recrea, la base
     * migrada la conserva y el abono "no se aplicó" vuelve a contar.
     */
    @Test
    fun `E-APP-092 la 36 a 37 recrea overdue_payments_view sin contar el abono -2`() {
        val antes = migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            sqlDeLaVista(db)
        }
        assertEquals(false, antes.contains("<> -2"))

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_36_37
        )
        val despues = sqlDeLaVista(migrada)
        migrada.close()

        assertEquals(true, despues.contains("p.DOCTO_CC_ID <> -2"))
    }

    private fun sqlDeLaVista(db: androidx.sqlite.db.SupportSQLiteDatabase): String = db.query(
        "SELECT sql FROM sqlite_master WHERE type = 'view' AND name = 'overdue_payments_view'"
    )
        .use { c -> if (c.moveToFirst()) c.getString(0) else "" }

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
