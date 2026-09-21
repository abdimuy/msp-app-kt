plugins {
    id("msp.android.library")
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.detekt")
    id("msp.kover")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.example.msp_app.feature.ventacorreccion"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale (Task 5+)
    }
}

// Sin bloque `kover {}` propio todavía: nace con el placeholder 0% de
// `msp.kover` (mismo patrón que `:feature:collectionReport` y
// `:feature:configuracion` al nacer). Task 7 mide y fija el piso real una
// vez que exista más que dominio puro — antes sería un número inventado.
// `verifyRoborazziDebug` tampoco entra al `prePushCheck` de la raíz todavía:
// sin goldens grabados (no hay UI hasta Task 5) el gate fallaría por falta
// de capturas, no por un defecto real.

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))

    // Task 3: `RoomVentaLocalCorreccionAdapter.guardarCorreccion` necesita
    // `db.withTransaction` (guardia + campos + merge de líneas + clearFailure
    // en UNA transacción, mismo patrón que `CobranzaSyncManager`/
    // `CobranzaReconciler` en `:app`). `:core:database` sólo expone Room como
    // `implementation`, así que no llega transitivo — se declara aquí.
    implementation(libs.bundles.room) // room-runtime + room-ktx

    testImplementation(project(":core:testing")) // FakeClock + fakes (api)
    testImplementation(libs.androidx.ui.test.junit4)
}
