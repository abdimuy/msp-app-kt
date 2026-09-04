plugins {
    id("msp.android.library")
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.test")
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

// Agrega la regla custom `money > NoDoubleForMoney` a este módulo — la promesa
// estructurada lleva un monto, así que maneja dinero igual que `:feature:pagos`:
// `config.from` SUMA este fragmento al `detekt.yml` que ya puso `msp.detekt`
// (un `setFrom` lo reemplazaría). El ruleset `money` solo aparece en el
// classpath de análisis porque abajo se declara
// `detektPlugins(project(":build-tools:detekt-rules"))`.
detekt {
    config.from(files("$rootDir/config/detekt/detekt-money.yml"))
}

android {
    namespace = "com.example.msp_app.feature.visitas"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common")) // DerivacionPeriodo, VisitaEnVentana, VisitScope, etc.
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:telemetry"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.navigation.compose) // NavController en la firma del screen
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel()

    testImplementation(project(":core:testing")) // fakes + Turbine + Robolectric + roborazzi (api)
    testImplementation(libs.androidx.ui.test.junit4)
    // roborazzi-compose declara androidx.activity:activity-compose como
    // compileOnly (no viene transitivo vía el `api` de :core:testing) — lo
    // necesita en runtime para hostear el composable en un ComponentActivity
    // real al capturar goldens, cuando las pantallas lleguen (Tasks 16+).
    testImplementation(libs.androidx.activity.compose)

    // Regla custom `NoDoubleForMoney` — registrada vía ServiceLoader, por eso
    // viaja como `detektPlugins` y no como dependencia normal. Pone el ruleset
    // `money` en el classpath de análisis de detekt de este módulo, que captura
    // el monto de la promesa.
    detektPlugins(project(":build-tools:detekt-rules"))
}
