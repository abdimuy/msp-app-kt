import java.util.Properties

plugins {
    id("msp.android.library")
    // Este módulo trae UI, con el mismo criterio que `:core:speech`: el suelo
    // del mapa, la atribución que ODbL obliga a pintar y la pantalla de
    // descarga del extracto son piezas del mapa, no de una feature — las usa
    // `:feature:pagos` hoy y cualquier pantalla que quiera un mapa mañana.
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.example.msp_app.core.mapas"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }

    defaultConfig {
        // De dónde se baja el extracto `.pmtiles`. NO hay default: hoy el
        // archivo no está publicado en ninguna parte (se genera a mano con
        // `go-pmtiles extract`, ver `huella-geografica-medida.md`), y escribir
        // una URL inventada haría que la pantalla de descarga prometa algo que
        // no existe. Vacío = `EstadoDelExtracto.SinOrigen`, que es la verdad.
        val origen = providers.gradleProperty("MAPA_EXTRACTO_URL").orNull
            ?: rootProject.file("local.properties").takeIf { it.isFile }?.let { archivo ->
                val propiedades = Properties()
                archivo.inputStream().use { propiedades.load(it) }
                propiedades.getProperty("MAPA_EXTRACTO_URL")
            }
            ?: ""
        buildConfigField("String", "EXTRACTO_DE_MAPA_URL", "\"$origen\"")
    }
}

dependencies {
    // Tokens de color/forma/tipografía: el estilo del mapa se deriva de
    // `MspTheme.colors`, no de una paleta paralela.
    implementation(project(":core:designsystem"))

    // NORMA DE ERRORES (vinculante en `:core:*`): cada `catch` emite su `code`.
    // Las COORDENADAS NUNCA viajan — ver `MapasTelemetria`.
    implementation(project(":core:telemetry"))

    // MapLibre Android, variante **OpenGL ES**.
    implementation(libs.maplibre.android)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.compose.foundation)
    // La flecha de volver de la pantalla de descarga (`Icons.AutoMirrored.Filled.ArrowBack`).
    // El set `core` y no `extended`: la flecha está en el core y `extended` pesa megas.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel() en el suelo
    implementation(libs.androidx.lifecycle.runtime.ktx) // LifecycleEventObserver del MapView

    // Descarga del extracto: OkHttp por el `Range` (reanudable) y WorkManager
    // por la restricción `UNMETERED`. El mismo par que `:core:speech` y
    // `:core:appgate` ya usan.
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
