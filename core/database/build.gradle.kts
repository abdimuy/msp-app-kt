plugins {
    id("msp.android.library")
    id("msp.hilt") // KSP + hilt-android + ksp(hilt-compiler)
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    id("msp.detekt") // ruleset completo (Plan 2: detekt-strict)
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.database"

    // MigrationTestHelper (SchemaIntegrityTest) lee el JSON exportado desde los
    // assets del propio test — sin este wiring solo lo verían tests
    // instrumentados (androidTest), no los unit tests Robolectric de este módulo.
    sourceSets {
        getByName("test") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

// Room exporta el esquema a un dir versionado (contrato de la DB). KSP recibe
// el room.schemaLocation; el `schemas/` se commitea (Task 2 genera el 27.json).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.bundles.room) // room-runtime + room-ktx
    ksp(libs.androidx.room.compiler)

    // room-testing (MigrationTestHelper) para los tests del propio módulo.
    // `msp.test` ya la agrega, pero se declara explícita aquí porque es un
    // requisito directo de este módulo (Task 4), no solo heredado del piso
    // común de testing.
    testImplementation(libs.androidx.room.testing)
    // ApplicationProvider (Robolectric in-memory DB smoke test, ver AppDatabaseTest).
    testImplementation(libs.bundles.android.test.support)
    testImplementation(project(":core:testing"))

    // Hilt-en-JVM para el graph test de DatabaseModule (Task 3): mismo par de
    // deps que :app usa para sus propios HiltAndroidTest sobre Robolectric.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
