import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("msp.android.library")
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    // Ruleset completo (Plan 2: detekt-strict); + money rule vía detektPlugins abajo.
    id("msp.detekt")
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
    // Mutación dirigida del envío de visitas (plan visitas P1, Tarea 17;
    // E-APP-076). No entra al prePushCheck: se corre a mano con
    // `./gradlew --no-parallel :core:common:pitestDebug`.
    alias(libs.plugins.pitest.android)
}

android {
    namespace = "com.example.msp_app.core.common"
}

// `:core:common` es el único módulo que hoy declara `detektPlugins(project(
// ":build-tools:detekt-rules"))` abajo, así que es el único con el ruleset
// `money` en su classpath de análisis. `config.from` AGREGA este archivo al
// que ya puso `msp.detekt` (`detekt.yml`) — un `config.setFrom` acá lo
// reemplazaría en vez de sumarlo. Ver `config/detekt/detekt-money.yml` para
// el porqué de la separación en dos archivos.
detekt {
    config.from(files("$rootDir/config/detekt/detekt-money.yml"))
}

// `msp.kover` deja un piso placeholder de 0% (ver KoverConventionPlugin) hasta
// que cada módulo tenga su propia línea base. `:core:common` es enteramente
// dominio puro (ver resolución del orquestador en el brief de Task 4: "~90%
// acotado exclusivamente al domain de :core:common"), así que aquí se suma
// una segunda regla —no se reemplaza la del piso— sobre TODO el package del
// módulo, no solo `sync`: cualquier clase de dominio nueva (p.ej.
// `core.common.text`) queda cubierta por el gate desde el día uno, en vez de
// quedar excluida en silencio.
extensions.configure<KoverProjectExtension> {
    reports {
        // Kover 0.8.x no permite override de filtros por regla individual
        // ("forbidden to override filters for a specific report") en módulos
        // Android, así que el filtro va a nivel de `reports`, común a todas
        // las reglas/reportes de este módulo. Se incluye el package raíz del
        // módulo completo y se excluye únicamente el `BuildConfig`
        // autogenerado por AGP (placeholder sin lógica, no forma parte del
        // dominio que este gate debe guardar).
        filters {
            includes {
                packages("com.example.msp_app.core.common")
            }
            excludes {
                classes("com.example.msp_app.core.common.BuildConfig")
            }
        }
        verify {
            rule("core-common domain coverage (Task 4)") {
                minBound(90)
            }
        }
    }
}

// `-PpitCompromisos` acota la mutación a la derivación de compromisos (plan
// visitas P2, Tarea 17; E-APP-104):
// `./gradlew --no-parallel :core:common:pitestDebug -PpitCompromisos`.
// Sin la propiedad, el alcance es el de P1 (envío de visitas).
val alcancePit: List<String> = if (providers.gradleProperty("pitCompromisos").isPresent) {
    listOf("com.example.msp_app.core.common.visitas.domain.*")
} else {
    listOf(
        "com.example.msp_app.core.common.visitas.envio.*",
        "com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase*"
    )
}

pitest {
    targetClasses.set(alcancePit)
    targetTests.set(alcancePit)
    threads.set(2)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
}

dependencies {
    // SyncAllPendingWorkUseCase (dominio de pendingwork) usa coroutineScope /
    // async / awaitAll / withTimeoutOrNull. Solo `-core` (JVM puro): el
    // dominio no depende de Android, así que NO se usa `kotlinx-coroutines-android`.
    implementation(libs.kotlinx.coroutines.core)

    // La decisión de envío de visitas (visitas/envio/DecisionDeEnvio.kt) LLAMA a
    // la tabla compartida `classifyUpload`; no la copia (plan visitas P1, Tarea 9).
    // `:core:upload` no depende de `:core:common` en main: no hay ciclo.
    implementation(project(":core:upload"))

    // El use case de pendingwork (SyncAllPendingWorkUseCase) usa runTest +
    // MainDispatcherRule en sus tests; :core:testing expone esas deps de test
    // (coroutines-test, turbine, junit) vía `api`.
    testImplementation(project(":core:testing"))

    // Los juegos de casos compartidos con msp-api (estado por venta, orden de precios y compromisos) son JSON.
    // Solo pruebas: el dominio de :core:common no parsea JSON.
    testImplementation(libs.gson)

    // Regla custom `NoDoubleForMoney` (Task 9) — registrada vía ServiceLoader,
    // por eso viaja como `detektPlugins` y no como dependencia normal.
    detektPlugins(project(":build-tools:detekt-rules"))
}
