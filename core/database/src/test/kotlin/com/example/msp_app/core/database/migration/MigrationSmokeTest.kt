package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.migrations.MIGRATION_20_21
import com.example.msp_app.core.database.migrations.MIGRATION_21_22
import com.example.msp_app.core.database.migrations.MIGRATION_22_23
import com.example.msp_app.core.database.migrations.MIGRATION_23_24
import com.example.msp_app.core.database.migrations.MIGRATION_24_25
import com.example.msp_app.core.database.migrations.MIGRATION_25_26
import com.example.msp_app.core.database.migrations.MIGRATION_26_27
import com.example.msp_app.core.testing.RobolectricTestBase
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SMOKE_DB_NAME = "migration-smoke-test.db"
private const val START_VERSION = 20
private const val SEEDED_PAYMENT_ID = "smoke-pago-001"
private const val SEEDED_PAYMENT_IMPORTE = "725.50"

/**
 * Smoke de las 7 migraciones reales (20→27) sobre una base sembrada por
 * `execSQL` crudo (spec Plan 2 Task 4 — decisión del orquestador: sin JSONs
 * históricos v20-v26, ver [SchemaIntegrityTest] para el detalle de esa
 * limitación). No usa `MigrationTestHelper` porque ese helper solo puede
 * `createDatabase` desde un JSON exportado, y v20 nunca exportó uno; en su
 * lugar se siembra el esquema mínimo real que cada `MIGRATION_x_y` necesita
 * (calcado del schema pre-migración usado por los tests históricos en
 * `:app` — ver `Migration20to21Test`/`Migration26to27Test`) usando el mismo
 * `SupportSQLiteOpenHelper.Factory` (`FrameworkSQLiteOpenHelperFactory`) que
 * Room usa por debajo, y se aplican los objetos `Migration` REALES en
 * secuencia — no una copia de su SQL.
 *
 * Qué rompería si este test fallara: cualquier migración de la cadena
 * 20→27 que hoy pasa silenciosamente porque nadie la ejecuta en secuencia
 * contra un esquema de partida real (columna con nombre distinto, tabla
 * prerrequisito faltante, orden de ALTER/DROP incorrecto). También sirve de
 * segunda red para money-safety: se siembra una fila de `Payment` no
 * subida ANTES de migrar y se verifica que sigue intacta después de las 7,
 * complementando a [PaymentSurvivalMigrationTest] (que prueba supervivencia
 * vía la ruta de Room/producción, no vía la cadena de migraciones cruda).
 */
class MigrationSmokeTest : RobolectricTestBase() {

    private lateinit var dbFile: File
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File(context.cacheDir, SMOKE_DB_NAME)
        dbFile.delete()

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.path)
            .callback(object : SupportSQLiteOpenHelper.Callback(START_VERSION) {
                override fun onCreate(db: SupportSQLiteDatabase) = seedStartingSchema(db)

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    // Las migraciones se disparan explícitamente en el test, no aquí:
                    // este callback nunca debería correr en este harness.
                    error(
                        "onUpgrade no debe dispararse: las migraciones se aplican a mano en el test"
                    )
                }
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    @After
    fun tearDown() {
        helper.close()
        dbFile.delete()
    }

    @Suppress("LongMethod")
    private fun seedStartingSchema(db: SupportSQLiteDatabase) {
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

        // Columnas minimas requeridas por overdue_payments_view (MIGRATION_21_22/22_23).
        db.execSQL(
            """
            CREATE TABLE sales (
                DOCTO_CC_ACR_ID INTEGER PRIMARY KEY NOT NULL,
                DOCTO_CC_ID INTEGER NOT NULL,
                FECHA TEXT NOT NULL,
                SALDO_REST REAL NOT NULL,
                PRECIO_TOTAL REAL NOT NULL,
                ENGANCHE REAL NOT NULL,
                PARCIALIDAD INTEGER NOT NULL,
                FREC_PAGO TEXT
            )
            """.trimIndent()
        )

        // Esquema pre-26→27, calcado de Migration26to27Test (:app) — la unica
        // migracion que toca Payment en esta cadena agrega PAGO_RECIBIDO_ID.
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

        // Prerrequisitos minimos de MIGRATION_24_25/25_26 (solo ALTER TABLE ADD COLUMN).
        db.execSQL("CREATE TABLE local_sale (LOCAL_SALE_ID TEXT PRIMARY KEY NOT NULL)")
        db.execSQL(
            """
            CREATE TABLE local_sale_products (
                LOCAL_SALE_ID TEXT NOT NULL,
                ARTICULO_ID INTEGER NOT NULL,
                PRIMARY KEY (LOCAL_SALE_ID, ARTICULO_ID)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE local_sale_combos (
                COMBO_ID TEXT NOT NULL,
                LOCAL_SALE_ID TEXT NOT NULL,
                PRIMARY KEY (COMBO_ID, LOCAL_SALE_ID)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE sale_image (
                LOCAL_SALE_IMAGE_ID TEXT PRIMARY KEY NOT NULL,
                LOCAL_SALE_ID TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun seedUnuploadedPayment(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO Payment (
                ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO,
                GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID,
                COBRADOR_ID, FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE
            ) VALUES (
                '$SEEDED_PAYMENT_ID', 'Efrain Dominguez Reyes', 48213, 91027, '2026-08-01T10:00:00Z',
                0, $SEEDED_PAYMENT_IMPORTE, NULL, NULL, 30144,
                7, 157, 21, 'Araceli Jimenez Cortes'
            )
            """.trimIndent()
        )
    }

    @Test
    fun `las 7 migraciones 20 a 27 corren en secuencia sin error SQL sobre un esquema sembrado`() {
        // Abrir dispara onCreate -> seedStartingSchema, deja el archivo en v20.
        val db = helper.writableDatabase
        seedUnuploadedPayment(db)

        listOf(
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27
        ).forEach { migration -> migration.migrate(db) }

        db.query("PRAGMA table_info(Payment)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(
                "PAGO_RECIBIDO_ID debe existir tras MIGRATION_26_27",
                columns.contains("PAGO_RECIBIDO_ID")
            )
        }

        db.query(
            "SELECT IMPORTE, GUARDADO_EN_MICROSIP, PAGO_RECIBIDO_ID FROM Payment WHERE ID = ?",
            arrayOf(SEEDED_PAYMENT_ID)
        ).use { cursor ->
            assertTrue(
                "el pago no subido sembrado antes de migrar debe seguir consultable al final",
                cursor.moveToFirst()
            )
            assertEquals(SEEDED_PAYMENT_IMPORTE.toDouble(), cursor.getDouble(0), 0.0)
            assertEquals(
                "GUARDADO_EN_MICROSIP no debe haber cambiado a subido por efecto de migrar",
                0,
                cursor.getInt(1)
            )
            assertTrue(
                "PAGO_RECIBIDO_ID debe quedar NULL para filas pre-existentes",
                cursor.isNull(2)
            )
        }
    }
}
