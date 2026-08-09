plugins {
    id("msp.android.library")
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.detekt")
    id("msp.kover")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

// Agrega la regla custom `money > NoDoubleForMoney` a este módulo — el piloto
// del reporte aloja el VO `Money` y el puente de datos `Money.of(Double)` — igual
// que `:core:common` / `:core:designsystem`: `config.from` SUMA este fragmento al
// `detekt.yml` que ya puso `msp.detekt` (un `setFrom` lo reemplazaría). El ruleset
// `money` solo aparece en el classpath de análisis porque abajo se declara
// `detektPlugins(project(":build-tools:detekt-rules"))`.
detekt {
    config.from(files("$rootDir/config/detekt/detekt-money.yml"))
}

android {
    namespace = "com.example.msp_app.feature.collectionreport"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:telemetry"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose) // si el screen expone NavController

    testImplementation(project(":core:testing")) // fakes + Turbine + Robolectric + roborazzi (api)
    testImplementation(libs.androidx.ui.test.junit4)

    // Regla custom `NoDoubleForMoney` (Task 9) — registrada vía ServiceLoader,
    // por eso viaja como `detektPlugins` y no como dependencia normal. Pone el
    // ruleset `money` en el classpath de análisis de detekt de este módulo, que
    // aloja el VO de dinero `Money` y el puente `Money.of(Double)`.
    detektPlugins(project(":build-tools:detekt-rules"))
}
