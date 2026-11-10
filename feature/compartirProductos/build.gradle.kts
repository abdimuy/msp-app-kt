plugins {
    id("msp.android.library")
    id("msp.android.compose")
    id("msp.test")
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

// La tarjeta que se manda por WhatsApp solo DIBUJA precios que ya clasificó
// `:core:common` (ClasificadorDePrecios); no cambia ventas, pagos ni precios.
// Aun así lleva la regla `money`: ningún monto viaja como Double/Float aquí.
detekt {
    config.from(files("$rootDir/config/detekt/detekt-money.yml"))
}

android {
    namespace = "com.example.msp_app.feature.compartirproductos"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + assets (fuentes)
    }
}

dependencies {
    implementation(project(":core:common")) // ClasificadorDePrecios/TipoDePrecio, AppTime
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.coil.compose)

    testImplementation(project(":core:testing")) // Robolectric + roborazzi + coroutines-test
    testImplementation(libs.androidx.ui.test.junit4)

    detektPlugins(project(":build-tools:detekt-rules"))
}
