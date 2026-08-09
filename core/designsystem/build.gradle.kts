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

// Mismo heap/metaspace que `msp.test` (TestConventionPlugin) sin aplicar el
// plugin completo: este módulo ya trae a mano el resto de lo que `msp.test`
// daría (isIncludeAndroidResources arriba + junit/coroutines-test/robolectric/
// turbine vía `api` de :core:testing, Task 1) y aplicar `msp.test` encima
// duplicaría esas deps. Roborazzi necesita heap real para renderizar
// (Robolectric Native Graphics) — con un solo golden (Task 5) pasa sin esto,
// pero Task 10 graba la matriz Tier×escala×tema completa en una sola JVM y
// sin este bump se queda corta de memoria.
tasks.withType<Test> {
    maxHeapSize = "2g"
    jvmArgs("-XX:MaxMetaspaceSize=1g")
}

dependencies {
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    // material3 y compose-ui ya vienen del bundle en msp.android.compose.

    // RobolectricTestBase + roborazzi (api) + junit + turbine.
    testImplementation(project(":core:testing"))
    // createComposeRule en test JVM (Roborazzi).
    testImplementation(libs.androidx.ui.test.junit4)
    // roborazzi-compose declara androidx.activity:activity-compose como
    // compileOnly (no viene transitivo vía el `api` de :core:testing) — lo
    // necesita en runtime para hostear el composable en un ComponentActivity
    // real al capturar (`captureRoboImage(filePath) { content }`).
    testImplementation(libs.androidx.activity.compose)
}
