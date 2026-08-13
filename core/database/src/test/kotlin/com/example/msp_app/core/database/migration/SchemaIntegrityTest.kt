package com.example.msp_app.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Rule
import org.junit.Test

private const val SCHEMA_TEST_DB = "schema-integrity-test.db"
private const val LATEST_SCHEMA_VERSION = 28

/**
 * Guardia de drift de esquema sobre v28 (spec Plan 2 Task 4). `MigrationTestHelper`
 * lee `core/database/schemas/.../28.json` (el commiteado por `exportSchema`, ver
 * `build.gradle.kts` de este módulo para el wiring de `sourceSets.test.assets`),
 * crea una base a partir de ese JSON y valida que "migrar" a la misma versión 27
 * (sin migraciones, `validateDroppedTables = true`) no encuentre tablas
 * huerfanas/faltantes. Si el JSON llegara corrupto, vacío o desalineado con el
 * propio `database.version` que declara, este test revienta.
 *
 * Limitación conocida (decisión del orquestador, ya resuelta — no reabrir): NO
 * existen JSONs históricos v20-v26 (`exportSchema` estuvo apagado hasta el plan
 * que introdujo el 27.json), así que este test SOLO puede validar v28 contra sí
 * mismo — no prueba que las migraciones existentes produzcan un esquema
 * idéntico al v28 real (eso lo cubren [PaymentSurvivalMigrationTest] y
 * [MigrationSmokeTest], que no dependen de JSONs históricos). La excepción es
 * la 27→28: es la primera con JSON de versión anterior real, y
 * [Migration27to28Test] sí la valida de punta a punta con ese harness.
 */
class SchemaIntegrityTest : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `el 28 json exportado coincide con el esquema real de AppDatabase`() {
        migrationTestHelper.createDatabase(SCHEMA_TEST_DB, LATEST_SCHEMA_VERSION).close()

        migrationTestHelper.runMigrationsAndValidate(SCHEMA_TEST_DB, LATEST_SCHEMA_VERSION, true)
    }
}
