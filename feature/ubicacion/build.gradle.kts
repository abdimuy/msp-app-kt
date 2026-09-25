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

android {
    namespace = "com.example.msp_app.feature.ubicacion"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }
}

dependencies {
    // La aritmética de agrupar vive aparte y se prueba sin mapa, sin red y sin
    // GL. Ver el KDoc de `AgrupadorDeLugares`: un radio mal aplicado manda al
    // cobrador a una puerta equivocada, y eso no se puede probar desde aquí.
    implementation(project(":core:geo"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:common")) // AppTime: la fecha del abono llega como texto
    implementation(project(":core:telemetry"))

    // **`play-services-maps` se declara AQUÍ y no en `:app`.**
    // `:feature:pagos` la evita a propósito —sus goldens fotografían las señas,
    // no un mapa con red y GL (ver el KDoc de `CuadroDeLaPuerta`)— y por eso el
    // cuadro chico entra por una ranura que cierra `:app`. Este módulo existe
    // PARA el mapa, así que la dependencia le toca y no contamina a nadie más.
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel()

    testImplementation(project(":core:testing")) // fakes + Turbine + Robolectric + roborazzi
    testImplementation(libs.androidx.ui.test.junit4)
}
