plugins {
    id("msp.android.library")
    id("msp.android.compose") // Compose + bundle compose-ui (incluye material3) + tooling debug
    id("msp.detekt") // ruleset completo (Plan 2: detekt-strict)
    id("msp.kover")
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.example.msp_app.core.designsystem"

    // Robolectric necesita ver res/font + qualifiers (fontScale, Tasks 5/10).
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    // material3 y compose-ui ya vienen del bundle en msp.android.compose.

    // RobolectricTestBase + roborazzi (api) + junit + turbine.
    testImplementation(project(":core:testing"))
    // createComposeRule en test JVM (Roborazzi).
    testImplementation(libs.androidx.ui.test.junit4)
}
