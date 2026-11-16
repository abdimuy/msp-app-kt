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
    namespace = "com.example.msp_app.feature.garantias"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    // El servicio Retrofit y su adaptador viven en el módulo (la lista de
    // archivos permitidos de `:app` no incluye un paquete `data/garantias`).
    // `:core:network` los declara como `implementation`, así que no llegan
    // transitivos y hay que pedirlos acá.
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    // `FileProvider` del `content://` que la cámara escribe: el adaptador de
    // cámara necesita `androidx.core` para construirlo.
    implementation(libs.androidx.core.ktx)
    // GPS de la recolección y de la entrega. Es la única dependencia Android
    // pesada del módulo y vive detrás del puerto `UbicacionDeGarantiaPort`.
    implementation(libs.play.services.location)
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:" +
            libs.versions.kotlinxCoroutines.get()
    ) // `Task.await()`; el catálogo no lo tiene porque en `:app` llega por Firebase KTX
    // Los adaptadores de la cola usan `AppDatabase.withTransaction` y las
    // entidades de Room; `:core:database` sólo expone Room como
    // `implementation`, así que no llega transitivo (mismo caso que
    // `:feature:ventaCorreccion`).
    implementation(libs.bundles.room)
    implementation(libs.gson) // parser del error de Huma (`errors[].message`)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.navigation.compose) // NavController en la firma del screen
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel()
    implementation(libs.androidx.activity.compose)

    testImplementation(project(":core:testing")) // fakes + Turbine + Robolectric + roborazzi (api)
    testImplementation(libs.androidx.ui.test.junit4)
}
