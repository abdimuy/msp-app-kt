plugins {
    id("msp.android.library")
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.test")
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint)
    // Goldens de la hoja del menú: claro x oscuro x las tres escalas (Task 5).
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.example.msp_app.core.navegacion"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }
}

// `msp.kover` deja el piso placeholder (0%). El piso real se mide y se fija en
// la Task 18 del plan docs/superpowers/plans/2026-10-06-menu-lateral.md, cuando
// el módulo ya tiene todo su código.

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common")) // BUSINESS_LOCALE (sumidero de versalitas)
    implementation(project(":core:telemetry"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.icons.lucide)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.activity.compose) // BackHandler del atrás predictivo

    testImplementation(project(":core:testing")) // fakes + Turbine + Robolectric + roborazzi (api)
    testImplementation(libs.androidx.ui.test.junit4)
}
