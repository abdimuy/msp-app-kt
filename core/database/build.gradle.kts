plugins {
    id("msp.android.library")
    id("msp.hilt") // KSP + hilt-android + ksp(hilt-compiler)
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.database"
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
    testImplementation(project(":core:testing"))
}
