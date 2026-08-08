import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("msp.android.library")
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.common"
}

// `msp.kover` deja un piso placeholder de 0% (ver KoverConventionPlugin) hasta
// que cada módulo tenga su propia línea base. El dominio de sync (pendingwork
// + health) ya tiene la cobertura TDD del Plan 1 Task 4, así que aquí se suma
// una segunda regla —no se reemplaza la del piso— acotada exclusivamente al
// package `...core.common.sync..**` (incluye pendingwork y health; NO afecta
// `core.common.text` ni módulos futuros de este mismo build.gradle.kts).
extensions.configure<KoverProjectExtension> {
    reports {
        // Kover 0.8.x no permite override de filtros por regla individual
        // ("forbidden to override filters for a specific report") en módulos
        // Android, así que el filtro va a nivel de `reports`, común a todas
        // las reglas/reportes de este módulo. Incluir solo el package `sync`
        // de paso excluye el `BuildConfig` autogenerado por AGP (placeholder
        // sin lógica, 0% de cobertura) y `core.common.text` (fuera del
        // alcance de esta tarea).
        filters {
            includes {
                packages("com.example.msp_app.core.common.sync")
            }
        }
        verify {
            rule("core-common sync domain coverage (Task 4)") {
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
}
