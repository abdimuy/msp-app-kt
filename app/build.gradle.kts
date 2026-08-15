import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    // Hilt aplicado directo (NO vía convention plugin `msp.hilt`): `:app` ya
    // aplica `alias(libs.plugins.ksp)` arriba para Room, y `msp.hilt` también
    // hace `pluginManager.apply("com.google.devtools.ksp")` — se evita el
    // doble-apply usando solo el alias de Hilt + sus deps a mano. `msp.hilt`
    // queda reservado para módulos nuevos `:core:*`/`:feature:*`.
    alias(libs.plugins.hilt.android)
}

fun loadProperties(file: File): Properties {
    val properties = Properties()
    if (file.exists()) {
        file.inputStream().use { input ->
            properties.load(input)
        }
    }
    return properties
}

val localProps = loadProperties(rootProject.file("local.properties"))

// ── LOCAL backend hosts (debug builds only) ────────────────────────────────
// Per-developer, overridable in local.properties (gitignored). Defaults target
// the Android emulator, where 10.0.2.2 is an alias for the host machine. On a
// physical device set LOCAL_API_HOST to your Mac's LAN IP. The Go API and the
// Node legacy must listen on different ports locally (both default to 3001 in
// their own repos, so the legacy here defaults to 3000).
val localApiHost = localProps.getProperty("LOCAL_API_HOST", "10.0.2.2")!!
val localV2Port = localProps.getProperty("LOCAL_V2_PORT", "3001")!!
val localLegacyPort = localProps.getProperty("LOCAL_LEGACY_PORT", "3000")!!
val localV2Url = "http://$localApiHost:$localV2Port/"
val localLegacyUrl = "http://$localApiHost:$localLegacyPort/"

fun gitShortSha(): String = try {
    ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(rootDir)
        .start().inputStream.bufferedReader().readText().trim()
        .ifEmpty { "nogit" }
} catch (e: Exception) {
    "nogit"
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = loadProperties(keystorePropertiesFile)

val mapsApiKey = localProps.getProperty("MAPS_API_KEY", "")!!
if (mapsApiKey.isEmpty()) {
    logger.warn("MAPS_API_KEY not found in local.properties")
    logger.warn("Please add your Google Maps API Key to local.properties:")
    logger.warn("MAPS_API_KEY=your_api_key_here")
    throw GradleException("MAPS_API_KEY is required in local.properties")
}

android {
    namespace = "com.example.msp_app"
    compileSdk = 35

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.msp_app"
        minSdk = 24
        targetSdk = 35
        versionCode = 56
        versionName = "2.16.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        getByName("release") {
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
            buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        }
    }

    // ── 3 entornos = 3 flavors (dimensión `environment`) ──────────────────────
    // El flavor ES el entorno; se elige en código (Build Variant) y cada uno es
    // de primera clase, independiente de debug/release:
    //
    //   devlocal  → Firebase dev  + APIs LOCALES (tu máquina, local.properties)
    //   devserver → Firebase dev  + APIs del server de pruebas (apidev/apidb)
    //   prod      → Firebase prod + APIs de producción
    //
    // `devlocal` se declara primero y va primero alfabéticamente (devlocal <
    // devserver < prod), así el variant por defecto que abre el ▶ de Android
    // Studio —sin tocar nada— es `devlocalDebug`. `devlocal` y `devserver`
    // comparten el proyecto Firebase dev y usan sufijo `.test` (su
    // google-services registra ese package). Nota: el flavor NO puede llamarse
    // "test" — AGP lo rechaza (choca con el source set de tests `src/test/`).
    flavorDimensions += "environment"
    productFlavors {
        create("devlocal") {
            dimension = "environment"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-local+${gitShortSha()}"
            resValue("string", "app_name", "msp-app LOCAL")
            // Hosts/puertos por desarrollador desde local.properties (def 10.0.2.2).
            buildConfigField("String", "V2_BASE_URL", "\"$localV2Url\"")
            buildConfigField("String", "LEGACY_BASE_URL", "\"$localLegacyUrl\"")
            buildConfigField("String", "IMAGES_BASE_URL", "\"https://mspimagenes.loclx.io/\"")
            // Pagos por el API v2 (msp-api Go). En test los pagos entran por
            // /v2/cobranza/pagos; un fallido queda capturado server-side.
            buildConfigField("boolean", "PAGOS_USE_V2", "true")
            // Visitas por el API v2 (msp-api Go), mismo host que pagos.
            buildConfigField("boolean", "VISITAS_USE_V2", "true")
        }
        // RETIRADO como entorno de pruebas remoto: su único túnel de API v2
        // (apidev.loclx.io) pasó a ser el de producción. Si este flavor
        // siguiera apuntando ahí con PAGOS_USE_V2=true, cualquier APK de
        // prueba en campo escribiría pagos en la base de producción.
        // Para probar contra un servidor usa `devlocal`, que toma host y
        // puerto de local.properties. No reactivar sin un túnel propio.
        create("devserver") {
            dimension = "environment"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-dev+${gitShortSha()}"
            resValue("string", "app_name", "msp-app DEV")
            buildConfigField("String", "V2_BASE_URL", "\"https://devserver-retirado.invalid/\"")
            buildConfigField("String", "LEGACY_BASE_URL", "\"https://apidb.loclx.io/\"")
            buildConfigField("String", "IMAGES_BASE_URL", "\"https://mspimagenes.loclx.io/\"")
            buildConfigField("boolean", "PAGOS_USE_V2", "false")
            buildConfigField("boolean", "VISITAS_USE_V2", "false")
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_name", "msp-app")
            buildConfigField("String", "LEGACY_BASE_URL", "\"https://msp2025.loclx.io/\"")
            // apidev.loclx.io dejó de ser el túnel de pruebas y ES el del API
            // Go de PRODUCCIÓN (no se consiguió un túnel adicional). Ya apunta
            // a la instancia que corre contra MUEBLERA_SNP.FDB en el puerto
            // 3011, con Firebase msp-db-1c2ce. El entorno de pruebas remoto
            // quedó retirado a cambio; ver el comentario del flavor devserver.
            buildConfigField("String", "V2_BASE_URL", "\"https://apidev.loclx.io/\"")
            buildConfigField("String", "IMAGES_BASE_URL", "\"https://mspimagenes.loclx.io/\"")
            // La condición que mantenía estos dos en false era "hasta que
            // exista un host Go de producción" (ver PendingPaymentsWorker).
            // Ya existe: es apidev.loclx.io, arriba. Captura de pagos y de
            // visitas pasan al Go, sin envío dual: el worker elige uno u otro.
            buildConfigField("boolean", "PAGOS_USE_V2", "true")
            buildConfigField("boolean", "VISITAS_USE_V2", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // La JVM forkeada de los unit tests usa el heap default (~512m),
            // insuficiente para la suite de Robolectric (cada test carga un
            // classloader de ~100M) → OOM/SIGSEGV. Le damos heap y metaspace.
            all {
                it.maxHeapSize = "2g"
                it.jvmArgs("-XX:MaxMetaspaceSize=1g")
            }
        }
    }
}

// `prod` solo se compila como RELEASE (APK de producción). Deshabilitamos
// `prodDebug` para que el ▶ de Android Studio —que siempre prefiere un variant
// debug— no pueda quedarse en prod: el único default posible es `devlocalDebug`
// (dev Firebase + API local). `devlocalRelease` tampoco tiene sentido.
androidComponents {
    beforeVariants { variant ->
        if (variant.name == "prodDebug" || variant.name == "devlocalRelease") {
            variant.enable = false
        }
        // Los unit tests corren sobre Robolectric y buena parte son de Compose
        // (`createComposeRule`), que necesita `androidx.compose.ui:ui-test-manifest`
        // para resolver `androidx.activity.ComponentActivity`. Ese artefacto es
        // debug-only POR DISEÑO — declara una Activity que no debe viajar en el
        // APK de producción — así que en un variant release el manifiesto no
        // existe y los tests truenan con "Unable to resolve activity ...
        // ComponentActivity" (robolectric/robolectric#4736).
        //
        // El source set de tests es UNO SOLO (`src/test`): el build type no
        // cambia qué se prueba, solo si la tarea puede levantar el host. Así
        // que la tarea de test del release se apaga y el gate es la debug
        // (`testDevlocalDebugUnitTest`), que sí corre la suite completa. Nada
        // de meter `ui-test-manifest` en `releaseImplementation`: eso mete
        // andamiaje de prueba en el APK que instalan los cobradores.
        variant.enableUnitTest = variant.buildType != "release"
    }
}

dependencies {

    implementation(project(":core:common"))
    implementation(project(":core:database"))
    // Política única de entrega garantizada (tabla de decisión + puerto de
    // verificación). Ver docs/module-standards/ENTREGA_GARANTIZADA.md.
    implementation(project(":core:upload"))
    // FontSizeLevel/LocalFontSizeLevel/LocalReduceMotion (fundación de Configuración) —
    // MainActivity los consume directo para el override Opción C de la raíz de composición
    // (no llegan transitivos: `:core:settings`/`:feature:collectionReport` los declaran
    // `implementation`, no `api`).
    implementation(project(":core:designsystem"))
    // ConnectivityMonitor + su módulo Hilt (Task 5, Plan 4) — mismo package
    // `com.example.msp_app.core.network`, ningún import de consumidor cambia.
    implementation(project(":core:network"))
    // Telemetry (puerto) + DurableTelemetry/StubTelemetrySink cableados por
    // Hilt vía TelemetryModule (Task 4, Plan 4) — Task 8 (cierre) cablea
    // LocalTelemetry en la raíz Compose de :app (MainActivity) para que
    // ScreenScope/Modifier.trackClick tengan un Telemetry real en toda la app.
    implementation(project(":core:telemetry"))
    // SettingsRepository (fundación de Configuración: tamaño de letra, privacidad,
    // reduce-motion — spec 2026-08-10-configuracion-tamano-letra-design.md). Consumido
    // por `MainActivity` (override Opción C de `LocalDensity` + `LocalReduceMotion`
    // en la raíz de composición).
    implementation(project(":core:settings"))
    // Piloto del reporte de cobranza unificado (Plan 5). `:app` provee el adapter
    // real de `UserCyclePort` (Firestore userData) en su composition root y monta
    // `CollectionReportScreen`/`...Tier2` en la ruta `daily_reports`.
    implementation(project(":feature:collectionReport"))
    // Pantalla de Configuración (tamaño de letra, tema, privacidad, reduce-motion —
    // spec 2026-08-10-configuracion-tamano-letra-design.md). `:app` provee el adapter
    // real de `AppThemePort` (ThemeController) y monta `ConfiguracionScreen` en la
    // ruta `configuracion` + el ítem del drawer.
    implementation(project(":feature:configuracion"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.icons.lucide)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.bundles.android.test.support)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.bundles.compose.test)
    testImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // ── instrumented e2e suite (payment-upload pipeline, B5/B6/B7) ─────────
    androidTestImplementation(libs.bundles.instrumented.e2e)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)

    // DataStore for draft saving
    implementation(libs.androidx.datastore.preferences)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.escpos.thermalprinter.android)
    implementation(libs.accompanist.permissions)
    implementation(libs.coil.compose)
    implementation(libs.commons.math3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.gson)
    implementation(libs.androidx.biometric)
}
