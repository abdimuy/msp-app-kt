plugins {
    id("msp.android.library")
    id("msp.hilt") // KSP + hilt-android + ksp(hilt-compiler)
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    id("msp.detekt") // ruleset completo (Plan 2: detekt-strict)
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.settings"
}

// `msp.kover` deja el piso placeholder (0%, ver `KoverConventionPlugin`): este
// módulo nuevo (fundación de Configuración, spec
// `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`)
// todavía no fija un umbral real — mismo criterio que `:core:network` Task 5:
// el gate real entra cuando la pantalla `:feature:configuracion` (agente
// posterior) termine de consumir este repositorio, no en el esqueleto solo.

dependencies {
    // FontSizeLevel: el nivel que persiste SettingsRepository.
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.datastore.preferences)

    testImplementation(project(":core:testing"))
    testImplementation(libs.bundles.unit.test)

    // Hilt-en-JVM para el graph test de SettingsModule, mismo par de deps que
    // `:core:database`/`:core:telemetry` usan para sus propios HiltAndroidTest
    // sobre Robolectric.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
