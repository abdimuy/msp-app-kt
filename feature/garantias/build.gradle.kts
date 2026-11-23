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

// Piso de cobertura REAL (reemplaza el placeholder 0% de `msp.kover`).
// El brief (línea 233) lo exige sobre `domain` y `data`; el módulo tiene además
// `ui/` y `di/`, que no entran al piso.
//
// GOTCHA de Kover 0.8 (el mismo que documenta `:core:database`): no se puede
// filtrar dentro de una regla (`verify { rule { filters { ... } } }` es error de
// compilación). El filtro vive en una variante de reporte propia
// (`dominioYDatos`) que se alimenta de la MISMA corrida de pruebas
// (`add("debug")`), así que no cuesta una ejecución extra: sólo re-filtra el
// mismo artefacto. Su tarea es `koverVerifyDominioYDatos`, que el gate de la
// raíz descubre sola (`koverVerify*` que no termina en `Release`).
//
// El módulo NO declara un trinquete de módulo: `ui/` (Compose) y `di/` no tienen
// un piso pedido, y uno bajo sería ruido. El único número que muerde es el de
// `domain`+`data`.
kover {
    currentProject {
        createVariant("dominioYDatos") { add("debug") }
    }
    reports {
        variant("dominioYDatos") {
            filters {
                includes {
                    packages(
                        "com.example.msp_app.feature.garantias.domain",
                        "com.example.msp_app.feature.garantias.data"
                    )
                }
            }
            verifyAppend {
                rule("feature-garantias: piso 85% LINE sobre domain/data") {
                    minBound(85)
                }
            }
        }
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
