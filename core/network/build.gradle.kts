plugins {
    id("msp.android.library")
    id("msp.hilt")
    id("msp.test")
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.example.msp_app.core.network"
}

// `msp.kover` deja el piso placeholder (0%, ver `KoverConventionPlugin`): este
// esqueleto (Task 5, Plan 4) solo reubica `ConnectivityMonitor` desde `:app`
// — sin domain nuevo propio que amerite fijar un umbral real todavía. El
// umbral real (y su entrada a `koverVerifyDebug`/`prePushCheck`) llega cuando
// T6+ construya el cliente Retrofit/interceptores sobre este esqueleto,
// mismo patrón que `:core:designsystem` (Task 1) y `:core:telemetry`
// (Task 1): el gate de cobertura entra con la línea base del dominio, no con
// el esqueleto del módulo.

dependencies {
    // T5 solo reubica `ConnectivityMonitor` (sin cliente HTTP todavía), pero
    // el `build.gradle.kts` completo del módulo se entrega ahora para que T6
    // (Retrofit/OkHttp base + interceptores) no tenga que reabrir este
    // archivo — mismo criterio de "esqueleto completo desde el día uno" que
    // usaron los otros módulos `:core:*` de este plan.
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)
    implementation(libs.okhttp)
    // NO firebase-auth aquí: el token bearer entra por el puerto AuthTokenProvider (T6),
    // cuya impl Firebase vive en :app — :core:network queda vendor-free y testeable con fake.

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(project(":core:testing"))
    testImplementation(libs.bundles.unit.test)
}
