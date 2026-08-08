package com.example.msp_app.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Rule
import org.junit.Test

private const val SCHEMA_TEST_DB = "schema-integrity-test.db"
private const val LATEST_SCHEMA_VERSION = 27

/**
 * Guardia de drift de esquema sobre v27 (spec Plan 2 Task 4). `MigrationTestHelper`
 * lee `core/database/schemas/.../27.json` (el commiteado por `exportSchema`, ver
 * `build.gradle.kts` de este módulo para el wiring de `sourceSets.test.assets`),
 * crea una base a partir de ese JSON y valida que "migrar" a la misma versión 27
 * (sin migraciones, `validateDroppedTables = true`) no encuentre tablas
 * huerfanas/faltantes. Si el JSON llegara corrupto, vacío o desalineado con el
 * propio `database.version` que declara, este test revienta.
 *
 * Limitación conocida (decisión del orquestador, ya resuelta — no reabrir): NO
 * existen JSONs históricos v20-v26 (`exportSchema` estuvo apagado hasta este
 * plan), así que este test SOLO puede validar v27 contra sí mismo — no prueba
 * que las 7 migraciones existentes produzcan un esquema idéntico al v27 real
 * (eso lo cubren [PaymentSurvivalMigrationTest] y [MigrationSmokeTest], que no
 * dependen de JSONs históricos). El valor real de este test es doble: (a)
 * confirma que el `27.json` exportado tras el hoist de `AppDatabase` a
 * `:core:database` sigue siendo válido y cargable, y (b) deja el harness
 * `MigrationTestHelper` + `schemas/` funcionando y probado para cuando llegue
 * la migración 28 — ahí sí habrá JSON de version anterior real y este mismo
 * patrón validará la migración incremental de punta a punta.
 */
class SchemaIntegrityTest : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `el 27 json exportado coincide con el esquema real de AppDatabase`() {
        migrationTestHelper.createDatabase(SCHEMA_TEST_DB, LATEST_SCHEMA_VERSION).close()

        migrationTestHelper.runMigrationsAndValidate(SCHEMA_TEST_DB, LATEST_SCHEMA_VERSION, true)
    }
}
