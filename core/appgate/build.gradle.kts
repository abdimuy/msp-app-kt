plugins {
    id("msp.android.library")
    // Añadido sobre el molde de `core/settings/build.gradle.kts`: este módulo
    // SÍ trae UI (la pantalla de bloqueo + la banda de cuenta regresiva). Vive
    // acá y no en un `:feature:*` propio porque la compuerta corre ANTES de
    // que cualquier feature se componga — un módulo Gradle extra para una sola
    // pantalla sin dominio propio no pagaría su costo.
    id("msp.android.compose")
    id("msp.hilt") // KSP + hilt-android + ksp(hilt-compiler)
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    id("msp.detekt") // ruleset completo (Plan 2: detekt-strict)
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.appgate"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }
}

// `msp.kover` deja el piso placeholder (0%, ver `KoverConventionPlugin`) —
// mismo criterio que `:core:settings`/`:feature:configuracion` al nacer: el
// umbral real se fija cuando la compuerta lleve una versión en campo y se
// conozca qué ramas se ejercitan de verdad, no sobre el esqueleto.

dependencies {
    // Tokens de color/tipografía/espaciado + LocalReduceMotion (la banda de
    // cuenta regresiva anima su tono y debe respetar la preferencia global).
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx) // FileProvider, para entregar el APK al instalador
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel() en la pantalla

    // Descarga del APK: OkHttp por la petición con `Range` (reanudación) y
    // WorkManager por la descarga automática en segundo plano restringida a
    // wifi (`NetworkType.UNMETERED`). `hilt-work` para que `ApkDownloadWorker`
    // reciba sus dependencias del grafo — `:app` ya cablea `HiltWorkerFactory`
    // en `MspApplication.workManagerConfiguration`.
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Fuente de configuración remota (`config/api_settings`). El puerto
    // `MinVersionConfigSource` NO depende de esto — solo la implementación.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore.ktx)

    testImplementation(project(":core:testing"))
    testImplementation(libs.bundles.unit.test)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver) // servidor real para probar `Range`/reanudación

    // WorkManager de verdad bajo Robolectric (`SynchronousExecutor`) para
    // probar la política de encolado de `UpdateDownloadScheduler`: KEEP vs.
    // REPLACE sólo existe frente a la cola real, y la fuente de verdad de
    // "qué paquete está encolado" es WorkManager, no un campo en memoria.
    testImplementation(libs.androidx.work.testing)

    // Hilt-en-JVM para el graph test de AppGateModule, mismo par de deps que
    // `:core:settings` usa para `SettingsModuleHiltGraphTest`.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
