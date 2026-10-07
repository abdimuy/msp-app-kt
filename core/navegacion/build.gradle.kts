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

// Piso de cobertura REAL (reemplaza el placeholder 0% de `msp.kover`).
// MEDIDO 2026-10-06 con `:core:navegacion:koverXmlReportDebug`: LINE 564/602 = 93.69%.
// Piso = medido - 3: trinquete, no meta.
kover {
    reports {
        verify {
            rule("core-navegacion: piso trinquete (medido 93.69% LINE)") {
                minBound(90)
            }
        }
    }
}

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
