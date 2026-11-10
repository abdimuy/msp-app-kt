plugins {
    id("msp.android.library")
    id("msp.hilt")
    id("msp.test")
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.example.msp_app.core.datos"
}

// Piso de cobertura: se fija con la línea base medida (ver el reporte de la
// función "uso de datos"); mientras tanto el placeholder de `msp.kover`.

dependencies {
    api(project(":core:common")) // AppClock / AppTime (zona de negocio)
    api(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.bundles.unit.test)
}
