plugins {
    id("msp.android.library")
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.detekt")
    id("msp.kover")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

// Agrega la regla custom `money > NoDoubleForMoney` a este módulo — el piloto
// del reporte aloja el VO `Money` y el puente de datos `Money.of(Double)` — igual
// que `:core:common` / `:core:designsystem`: `config.from` SUMA este fragmento al
// `detekt.yml` que ya puso `msp.detekt` (un `setFrom` lo reemplazaría). El ruleset
// `money` solo aparece en el classpath de análisis porque abajo se declara
// `detektPlugins(project(":build-tools:detekt-rules"))`.
detekt {
    config.from(files("$rootDir/config/detekt/detekt-money.yml"))
}

android {
    namespace = "com.example.msp_app.feature.collectionreport"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }
}

// Piso de cobertura REAL (reemplaza el placeholder 0% de `msp.kover`, que
// hacía pasar `koverVerifyDebug` sin medir nada).
//
// MEDIDO 2026-08-16 con `:feature:collectionReport:koverXmlReportDebug`:
//   LINE 2890/3559 = 81.20%   (INSTRUCTION 81.60%, BRANCH 63.24%)
// Los huecos conocidos: `PdfCanvasRenderer` (234 líneas, 0% — dibuja sobre un
// `Canvas` de Android real y no tiene prueba de render todavía),
// `CollectionReportScreenKt` (51.2%) y `ReportActionsController` (50.0%).
//
// Piso = 78, ~3 puntos por debajo de lo medido: trinquete, no meta. Este es el
// módulo de las cifras que el cobrador lee en pantalla; el piso evita el
// retroceso silencioso y la fidelidad visual la sigue cuidando
// `verifyRoborazziDebug`, que ya vive en `prePushCheck`.
kover {
    reports {
        verify {
            rule("feature-collectionReport: piso trinquete (medido 81.20% LINE, 2026-08-16)") {
                minBound(78)
            }
        }
    }
}

// Mismo heap/metaspace que `:core:designsystem` (msp.detekt/msp.test no lo dan por
// default aquí): Task 6 graba la primera matriz de goldens Roborazzi de este módulo
// (header+periodo+subrow+hero, Tier 1 light+dark) en la misma JVM de test — sin este
// bump el render de Robolectric Native Graphics se queda corto de memoria.
tasks.withType<Test> {
    maxHeapSize = "2g"
    jvmArgs("-XX:MaxMetaspaceSize=1g")
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:telemetry"))
    // Impresión térmica (P2): puertos/gateway/store del stack hexagonal de :core:printing
    // (PrinterPort, PreferredPrinterStore, ReportTicketFormatter/TicketRenderer, PrintError).
    implementation(project(":core:printing"))
    implementation(libs.androidx.compose.foundation)
    // rememberLauncherForActivityResult + RequestMultiplePermissions (P2): el request del
    // permiso de Bluetooth para imprimir vive en CollectionReportScreen.
    implementation(libs.androidx.activity.compose)
    // FileProvider (Task 8, ReportActionsController): compartir/abrir el PDF generado en
    // cache sin exponer una `file://` Uri cruda — reusa la declaración de
    // `androidx.core.content.FileProvider` que `:app` ya trae en su manifest
    // (`${applicationId}.fileprovider`, `cache-path path="."`), no se agrega una nueva.
    implementation(libs.androidx.core.ktx)
    // ModalBottomSheet (Task 8, ReportSheets) — Compose Material3 ya viene del bundle
    // `compose-ui` que aplica `msp.android.compose` (androidx-material3), no se repite aquí.
    // Menu/DateRange del header y subrow — 1:1 mockup.
    implementation(libs.androidx.compose.material.icons.core)
    // Lucide (Task 1/2, enriquecimiento de filas): tiles de método de pago + íconos del hero
    // (reemplaza los emojis 📊/⚡/🕘/🎯) — mismo paquete que ya usa `:app`.
    implementation(libs.icons.lucide)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.navigation.compose) // si el screen expone NavController
    // hiltViewModel() en CollectionReportScreen.
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(project(":core:testing")) // fakes + Turbine + Robolectric + roborazzi (api)
    testImplementation(libs.androidx.ui.test.junit4)
    // roborazzi-compose declara androidx.activity:activity-compose como compileOnly (no
    // viene transitivo vía el `api` de :core:testing) — lo necesita en runtime para
    // hostear el composable en un ComponentActivity real al capturar goldens (Task 6+).
    testImplementation(libs.androidx.activity.compose)

    // Regla custom `NoDoubleForMoney` (Task 9) — registrada vía ServiceLoader,
    // por eso viaja como `detektPlugins` y no como dependencia normal. Pone el
    // ruleset `money` en el classpath de análisis de detekt de este módulo, que
    // aloja el VO de dinero `Money` y el puente `Money.of(Double)`.
    detektPlugins(project(":build-tools:detekt-rules"))
}
