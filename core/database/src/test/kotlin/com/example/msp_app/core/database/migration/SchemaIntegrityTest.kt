package com.example.msp_app.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Rule
import org.junit.Test

private const val SCHEMA_TEST_DB = "schema-integrity-test.db"
private const val LATEST_SCHEMA_VERSION = 31

/**
 * Guardia de drift de esquema sobre v31 (spec Plan 2 Task 4; v30 la trajo el
 * plan "pagos y visitas", v31 la migración renumerada del plan "Corregir una
 * venta antes de que suba" — ver el KDoc de `MIGRATION_30_31`).
 * `MigrationTestHelper` lee `core/database/schemas/.../31.json` (el commiteado
 * por `exportSchema`, ver `build.gradle.kts` de este módulo para el wiring de
 * `sourceSets.test.assets`), crea una base a partir de ese JSON y valida que
 * "migrar" a la misma versión (sin migraciones, `validateDroppedTables = true`)
 * no encuentre tablas huerfanas/faltantes. Si el JSON llegara corrupto, vacío o
 * desalineado con el propio `database.version` que declara, este test revienta.
 *
 * Limitación conocida (decisión del orquestador, ya resuelta — no reabrir): NO
 * existen JSONs históricos v20-v26 (`exportSchema` estuvo apagado hasta el plan
 * que introdujo el 27.json), así que este test SOLO puede validar la última
 * versión contra sí misma — no prueba que las migraciones existentes produzcan
 * un esquema idéntico al real (eso lo cubren [PaymentSurvivalMigrationTest] y
 * [MigrationSmokeTest], que no dependen de JSONs históricos). Las excepciones
 * son la 27→28, la 28→29, la 29→30 y la 30→31: ya cuentan con el JSON de la
 * versión anterior, así que [Migration27to28Test], [Migration28to29Test],
 * [Migration29to30Test] y [Migration30to31Test] sí las validan de punta a punta
 * con ese harness.
 */
class SchemaIntegrityTest : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `el 31 json exportado coincide con el esquema real de AppDatabase`() {
        migrationTestHelper.createDatabase(SCHEMA_TEST_DB, LATEST_SCHEMA_VERSION).close()

        migrationTestHelper.runMigrationsAndValidate(SCHEMA_TEST_DB, LATEST_SCHEMA_VERSION, true)
    }
}
