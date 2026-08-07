import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    alias(libs.plugins.ksp)
    id("org.jlleitschuh.gradle.ktlint")
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
        versionCode = 50
        versionName = "2.12.2"

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
        create("devserver") {
            dimension = "environment"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-dev+${gitShortSha()}"
            resValue("string", "app_name", "msp-app DEV")
            buildConfigField("String", "V2_BASE_URL", "\"https://apidev.loclx.io/\"")
            buildConfigField("String", "LEGACY_BASE_URL", "\"https://apidb.loclx.io/\"")
            buildConfigField("String", "IMAGES_BASE_URL", "\"https://mspimagenes.loclx.io/\"")
            buildConfigField("boolean", "PAGOS_USE_V2", "true")
            buildConfigField("boolean", "VISITAS_USE_V2", "true")
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_name", "msp-app")
            buildConfigField("String", "LEGACY_BASE_URL", "\"https://msp2025.loclx.io/\"")
            // TODO: confirmar el host real del Go de prod cuando se despliegue.
            buildConfigField("String", "V2_BASE_URL", "\"https://todo-go-prod-host.invalid/\"")
            buildConfigField("String", "IMAGES_BASE_URL", "\"https://mspimagenes.loclx.io/\"")
            // Prod sigue por el backend legacy hasta que exista el Go de prod
            // (V2_BASE_URL de arriba es un placeholder inválido a propósito).
            buildConfigField("boolean", "PAGOS_USE_V2", "false")
            buildConfigField("boolean", "VISITAS_USE_V2", "false")
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
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("com.composables:icons-lucide:1.0.0")
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:runner:1.5.2")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.work:work-testing:2.10.2")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // ── instrumented e2e suite (payment-upload pipeline, B5/B6/B7) ─────────
    androidTestImplementation("androidx.work:work-testing:2.10.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // DataStore for draft saving
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("androidx.compose.foundation:foundation:1.7.8")
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    implementation("com.google.maps.android:maps-compose:4.2.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")
    implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.4.0")
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("androidx.biometric:biometric:1.1.0")
}
