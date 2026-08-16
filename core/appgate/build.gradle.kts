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

// Piso de cobertura REAL (reemplaza el placeholder 0% de `msp.kover`, que
// hacía pasar `koverVerifyDebug` sin decir absolutamente nada).
//
// MEDIDO 2026-08-16 con `:core:appgate:koverXmlReportDebug`:
//   LINE 518/822 = 63.02%   (INSTRUCTION 65.66%, BRANCH 58.87%)
// Desglose honesto de lo que NO está cubierto: `VersionGateViewModel`
// (54 líneas, 0%), `ApkDownloadWorker` (23, 0%),
// `FirestoreMinVersionConfigSourceKt` (19, 0%), `UpdateFileLocator` (13, 0%)
// — es decir, todo lo que toca Firestore/WorkManager/instalador real. Si se
// descuenta lo generado (Dagger/Hilt/BuildConfig: 102 líneas al 3.9%), el
// código propio va en 71.39%; deliberadamente NO se agrega ese filtro aquí:
// subir el número con un `excludes` sin subir la cobertura es el mismo
// maquillaje que este cambio viene a quitar.
//
// Piso = 60, tres puntos por debajo de lo medido. Es un TRINQUETE (impedir
// que se retroceda), no una meta: un piso clavado en 63 se rompería con
// cualquier oscilación del render de Robolectric y el equipo aprendería a
// ignorarlo o a bajarlo, que es peor que no tenerlo.
kover {
    reports {
        verify {
            rule("core-appgate: piso trinquete (medido 63.02% LINE, 2026-08-16)") {
                minBound(60)
            }
        }
    }
}

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
