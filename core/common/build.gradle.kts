import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("msp.android.library")
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
    alias(libs.plugins.detekt) // Task 9: gate "anti-Double para dinero"
}

android {
    namespace = "com.example.msp_app.core.common"
}

// Task 9 (Plan 1, spec §13 Tier A): `:core:common` es el primer módulo de
// dominio nuevo, así que es donde se arma el gate "anti-Double para dinero"
// (money VOs reales llegan en Plan 5). `buildUponDefaultConfig = false` +
// `config/detekt/detekt.yml` (que solo activa `money > NoDoubleForMoney`)
// evitan que el ruleset por defecto de detekt (naming, complexity, style...)
// explote sobre código que no es el objetivo de esta tarea — ver el archivo
// de config para el razonamiento completo.
detekt {
    buildUponDefaultConfig = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
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

dependencies {
    // SyncAllPendingWorkUseCase (dominio de pendingwork) usa coroutineScope /
    // async / awaitAll / withTimeoutOrNull. Solo `-core` (JVM puro): el
    // dominio no depende de Android, así que NO se usa `kotlinx-coroutines-android`.
    implementation(libs.kotlinx.coroutines.core)

    // El use case de pendingwork (SyncAllPendingWorkUseCase) usa runTest +
    // MainDispatcherRule en sus tests; :core:testing expone esas deps de test
    // (coroutines-test, turbine, junit) vía `api`.
    testImplementation(project(":core:testing"))

    // Regla custom `NoDoubleForMoney` (Task 9) — registrada vía ServiceLoader,
    // por eso viaja como `detektPlugins` y no como dependencia normal.
    detektPlugins(project(":build-tools:detekt-rules"))
}
