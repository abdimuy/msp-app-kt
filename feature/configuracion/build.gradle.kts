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

// Piso de cobertura REAL (reemplaza el placeholder 0% de `msp.kover`).
//
// MEDIDO 2026-08-16 con `:feature:configuracion:koverXmlReportDebug`:
//   LINE 273/311 = 87.78%   (INSTRUCTION 87.34%, BRANCH 50.00%)
// Es el módulo mejor cubierto de los cinco que se tocaron en este cambio.
// Lo que falta es casi todo lambda de navegación de `ConfiguracionScreenKt`
// (82.8%) y las fábricas de Hilt (15 líneas generadas, 0%).
//
// Piso = 85, ~3 puntos por debajo de lo medido: trinquete, no meta.
//
// OJO — este módulo estaba en `settings.gradle.kts` pero NO en la tarea
// `prePushCheck` de la raíz: ni ktlint, ni detekt, ni sus pruebas, ni kover
// corrían en la compuerta local. Se cierra ese hueco en el mismo cambio que
// pone este piso; un piso que nadie ejecuta no es un piso.
kover {
    reports {
        verify {
            rule("feature-configuracion: piso trinquete (medido 87.78% LINE, 2026-08-16)") {
                minBound(85)
            }
        }
    }
}

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
