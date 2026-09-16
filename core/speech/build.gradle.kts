plugins {
    id("msp.android.library")
    // Este módulo SÍ trae UI, con el mismo criterio que `:core:appgate`: el
    // campo con borde vivo y la pantalla de descarga del modelo son piezas del
    // dictado, no de una feature — las usa `:feature:visitas` hoy y cualquier
    // campo de texto del repo mañana. Un `:feature:*` propio para dos
    // composables sin dominio de negocio no pagaría su costo.
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.example.msp_app.core.speech"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }
}

dependencies {
    // Tokens de color/forma/tipografía + `LocalReduceMotion`: el borde vivo gira
    // y tiene que dejar de girar con movimiento reducido.
    implementation(project(":core:designsystem"))

    // NORMA DE ERRORES (vinculante en `:core:*`): cada `catch` de este módulo
    // emite su `code`. El texto dictado NUNCA viaja — ver `SpeechTelemetria`.
    implementation(project(":core:telemetry"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx) // ContextCompat.checkSelfPermission
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel() en la pantalla
    implementation(libs.androidx.activity.compose) // rememberLauncherForActivityResult (permiso)

    // Descarga del modelo: OkHttp por el `Range` (reanudable) y WorkManager por
    // la restricción `UNMETERED` — que es como se cumple, de verdad y no de
    // palabra, la promesa "se descarga una vez, solo con wifi" del mock.
    // Mismo par que `:core:appgate` ya usa para el APK.
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(project(":core:testing")) // RecordingTelemetry + Robolectric + roborazzi
    testImplementation(libs.bundles.unit.test)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver) // servidor real para probar el `Range`
    testImplementation(libs.androidx.work.testing) // la cola real, para la política de encolado
}
