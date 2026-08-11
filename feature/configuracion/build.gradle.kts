plugins {
    id("msp.android.library")
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.detekt")
    id("msp.kover")
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.example.msp_app.feature.configuracion"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale (preview en vivo)
    }
}

// `msp.kover` deja el piso placeholder (0%) — módulo nuevo (Configuración,
// spec `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`),
// mismo criterio que `:core:settings`: el gate real entra cuando el ViewModel +
// la pantalla queden con la cobertura de Tasks 1-2 completa, no antes.

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:settings"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.navigation.compose) // NavController en la firma del screen
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel() en ConfiguracionScreen

    testImplementation(project(":core:testing")) // fakes + Robolectric + compose test (api)
    testImplementation(libs.androidx.ui.test.junit4)
}
