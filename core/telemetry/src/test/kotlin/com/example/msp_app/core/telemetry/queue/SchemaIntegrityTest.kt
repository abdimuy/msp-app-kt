package com.example.msp_app.core.telemetry.queue

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Rule
import org.junit.Test

private const val SCHEMA_TEST_DB = "telemetry-schema-integrity-test.db"
private const val LATEST_SCHEMA_VERSION = 1

/**
 * Guardia de drift de esquema sobre `telemetry_db` v1 — análogo a
 * `com.example.msp_app.core.database.migration.SchemaIntegrityTest` de
 * `:core:database`, mismo mecanismo (`MigrationTestHelper` lee el JSON
 * exportado desde `core/telemetry/schemas/.../1.json`, commiteado — ver el
 * wiring `sourceSets.test.assets` en `build.gradle.kts` de este módulo).
 *
 * Al ser v1 (primer schema, sin migraciones todavía) esta prueba solo puede
 * validar "el 1.json exportado sigue siendo cargable y coincide con
 * `TelemetryDatabase` real" — el mismo alcance limitado que documenta la
 * versión de `:core:database` para su v27, con la diferencia de que acá SÍ
 * es la primera versión (no hay historia de migraciones que validar contra
 * JSONs anteriores porque no existen todavía). El valor real: deja el
 * harness `MigrationTestHelper`/`schemas/` probado desde el día uno de este
 * store, listo para cuando llegue una v2 real.
 */
class SchemaIntegrityTest : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TelemetryDatabase::class.java
    )

    @Test
    fun `el 1 json exportado coincide con el esquema real de TelemetryDatabase`() {
        migrationTestHelper.createDatabase(SCHEMA_TEST_DB, LATEST_SCHEMA_VERSION).close()

        migrationTestHelper.runMigrationsAndValidate(SCHEMA_TEST_DB, LATEST_SCHEMA_VERSION, true)
    }
}
