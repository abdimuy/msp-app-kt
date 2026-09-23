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
// `verifyRoborazziDebug` SÍ entra al `prePushCheck` de la raíz desde Task 5:
// ya hay goldens grabados (`BotonCorregir`/`AvisoNoCorregible`, matriz
// tema × escala en `CorreccionScreenshotTest`).

// Mismo heap/metaspace que `:core:designsystem`/`:feature:collectionReport`
// (`msp.detekt`/`msp.test` no lo dan por default aquí): Task 5 graba la
// primera matriz de goldens Roborazzi de este módulo en la misma JVM de
// test — sin este bump el render de Robolectric Native Graphics se queda
// corto de memoria.
tasks.withType<Test> {
    maxHeapSize = "2g"
    jvmArgs("-XX:MaxMetaspaceSize=1g")
}

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
    // roborazzi-compose declara androidx.activity:activity-compose como
    // compileOnly (no viene transitivo vía el `api` de :core:testing) — lo
    // necesita en runtime para hostear el composable en un ComponentActivity
    // real al capturar goldens (Task 5, `CorreccionScreenshotTest`). Mismo
    // gotcha que `:feature:collectionReport`.
    testImplementation(libs.androidx.activity.compose)
}
