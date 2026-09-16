plugins {
    id("msp.android.library")
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.test")
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

// Agrega la regla custom `money > NoDoubleForMoney` a este módulo — maneja
// cobranza (pagos), igual que `:core:common` / `:core:designsystem` /
// `:feature:collectionReport`: `config.from` SUMA este fragmento al
// `detekt.yml` que ya puso `msp.detekt` (un `setFrom` lo reemplazaría). El
// ruleset `money` solo aparece en el classpath de análisis porque abajo se
// declara `detektPlugins(project(":build-tools:detekt-rules"))`.
detekt {
    config.from(files("$rootDir/config/detekt/detekt-money.yml"))
}

android {
    namespace = "com.example.msp_app.feature.pagos"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    // Money (Task 15: elevado desde :feature:collectionReport).
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:telemetry"))
    // Impresion termica (Task 20): PrinterPort/PreferredPrinterStore/PrintLogStore +
    // la regla del dia del cobro y el registro de impresiones. La Task 15 quito
    // esta dependencia porque el modulo no tenia consumidor todavia; esta tarea
    // trae el consumidor (el ticket de pago) junto con ella.
    implementation(project(":core:printing"))
    // El suelo del bloque de mapa del detalle de cliente (`:core:mapas`). Entra
    // por el slot `suelo` de `MapaDelCliente`, que ya existia: `DetalleClienteScreen`
    // pone el suelo cableado y `DetalleClienteContent` —el que fotografian los
    // goldens— se queda con el suelo liso.
    implementation(project(":core:mapas"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.navigation.compose) // NavController en la firma del screen
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel()
    // `rememberLauncherForActivityResult` + `ActivityResultContracts.TakePicture`
    // (Task 22): la cámara del comprobante. Estaba solo como `testImplementation`
    // —roborazzi-compose lo declara `compileOnly` y lo necesita en runtime para
    // hostear los goldens—; ahora hace falta también en producción, y
    // `testImplementation` extiende `implementation`, así que esta línea cubre
    // los dos usos y la de abajo se retira.
    implementation(libs.androidx.activity.compose)

    testImplementation(project(":core:testing")) // fakes + Turbine + Robolectric + roborazzi (api)
    testImplementation(libs.androidx.ui.test.junit4)

    // Regla custom `NoDoubleForMoney` — registrada vía ServiceLoader, por eso
    // viaja como `detektPlugins` y no como dependencia normal. Pone el
    // ruleset `money` en el classpath de análisis de detekt de este módulo,
    // que maneja cobranza.
    detektPlugins(project(":build-tools:detekt-rules"))
}
