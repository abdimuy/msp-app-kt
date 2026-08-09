import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("msp.android.compose") // aplica msp.android.library + Compose (trackClick/ScreenScope, T4)
    id("msp.hilt") // KSP + hilt-android + ksp(hilt-compiler)
    id("msp.test") // DESPUÉS de la library
    id("msp.kover")
    id("msp.detekt") // ruleset completo (Plan 2: detekt-strict)
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.telemetry"
}

// `msp.kover` deja un piso placeholder de 0% (ver KoverConventionPlugin) hasta
// que cada módulo tenga su propia línea base. Task 2 es dominio puro (puerto
// `Telemetry` + VO `TelemetryEvent`/`TelemetryEventType`) — se activa el piso
// real del módulo aquí, igual que `:core:common` (Task 4).
extensions.configure<KoverProjectExtension> {
    reports {
        filters {
            includes {
                packages("com.example.msp_app.core.telemetry")
            }
            excludes {
                classes("com.example.msp_app.core.telemetry.BuildConfig")
                // `Telemetry$DefaultImpls`: sintético que Kotlin genera para los
                // parámetros con default de una interfaz (`props = emptyMap()`
                // en `event`/`error`). Llamadas Kotlin→Kotlin (todas las de este
                // módulo) resuelven el default por inlining en el call site, así
                // que este método nunca se invoca en runtime — no es lógica
                // nuestra sin testear, es un artefacto del compilador (mismo
                // trato que `BuildConfig`).
                classes("com.example.msp_app.core.telemetry.Telemetry\$DefaultImpls")
            }
        }
        verify {
            rule("core-telemetry domain coverage (Task 2)") {
                minBound(90)
            }
        }
    }
}

// La cola durable de telemetría es un store Room PROPIO (`telemetry_db`),
// independiente de `msp_db` (v27, :core:database) — NUNCA lo referencia.
// Exporta su schema a un dir versionado (contrato de su DB), como
// :core:database. Sin `@Database` todavía (T2-T4), Room no genera JSON aún.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.bundles.room) // room-runtime + room-ktx
    ksp(libs.androidx.room.compiler)

    // AppClock/AppTime (java.time) para timestamps de eventos de telemetría.
    implementation(project(":core:common"))

    // room-testing (MigrationTestHelper) para los tests del propio módulo,
    // igual que :core:database — `msp.test` ya la agrega, pero se declara
    // explícita porque es un requisito directo de este módulo.
    testImplementation(libs.androidx.room.testing)
    testImplementation(project(":core:testing"))
}
